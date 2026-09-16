if (url.pathname === "/api/customer-delete" && request.method === "POST") {
  const auth = request.headers.get("Authorization") || "";
  const token = auth.startsWith("Bearer ") ? auth.slice(7) : "";
  if (!token) return json({ error: "Authentication required" }, 401);

  const user = await verifySupabaseUser(token);
  if (!user?.id) return json({ error: "Invalid session" }, 401);

  let body;
  try {
    body = await request.json();
  } catch {
    return json({ error: "Invalid JSON" }, 400);
  }

  const customerId = String(body.customer_id || "").trim();
  if (!customerId) {
    return json({ error: "customer_id is required" }, 400);
  }

  try {
    const selected = await env.DB.prepare(
      `SELECT * FROM customers
       WHERE id = ? AND owner_id = ?
       LIMIT 1`
    ).bind(customerId, user.id).first();

    if (!selected) {
      return json({ error: "Customer not found" }, 404);
    }

    const allResult = await env.DB.prepare(
      `SELECT id, notes, main_customer_id, parent_customer_id,
              parent_id, main_id
       FROM customers
       WHERE owner_id = ?`
    ).bind(user.id).all();

    const allCustomers = allResult.results || [];

    function parentIdOf(c) {
      if (!c) return "";

      const notes = String(c.notes || "");

      const marker = notes.match(
        /(?:^|\|)\s*(?:NF_MULTI_PARENT|NF_PARENT_CUSTOMER)\s*:\s*([0-9a-f-]{20,})/i
      );

      if (marker?.[1]) {
        return String(marker[1]).trim();
      }

      return String(
        c.main_customer_id ||
        c.parent_customer_id ||
        c.parent_id ||
        c.main_id ||
        ""
      ).trim();
    }

    const selectedParent = parentIdOf(selected);
    const isAdditional = !!selectedParent;

    /*
     * Main delete:
     *   Main + ALL descendants
     *
     * Additional delete:
     *   ONLY selected Additional
     */
    const customerIds = new Set([customerId]);

    if (!isAdditional) {
      let changed = true;

      while (changed) {
        changed = false;

        for (const c of allCustomers) {
          const cid = String(c?.id || "").trim();
          if (!cid || customerIds.has(cid)) continue;

          const parent = parentIdOf(c);

          if (parent && customerIds.has(parent)) {
            customerIds.add(cid);
            changed = true;
          }
        }
      }
    }

    const customerIdList = [...customerIds];
    const cp = customerIdList.map(() => "?").join(",");

    /*
     * Find ALL orders belonging to these customers.
     */
    const orderCols = await tableColumns(env.DB, "orders");

    const orderCustomerCols = [
      "customer_id",
      "customerId",
      "cust_id",
      "custId",
      "client_id",
      "clientId"
    ].filter(c => orderCols.has(c));

    let orderIds = [];

    if (orderCustomerCols.length) {
      const links = orderCustomerCols
        .map(c => `${safeCol(c)} IN (${cp})`)
        .join(" OR ");

      const result = await env.DB.prepare(
        `SELECT id FROM orders
         WHERE owner_id = ? AND (${links})`
      ).bind(user.id, ...customerIdList).all();

      orderIds = (result.results || [])
        .map(r => String(r.id || "").trim())
        .filter(Boolean);
    }

    const [
      paymentCols,
      alterationCols,
      measurementCols,
      designCols,
      calculationCols
    ] = await Promise.all([
      tableColumns(env.DB, "payments"),
      tableColumns(env.DB, "alterations"),
      tableColumns(env.DB, "measurements"),
      tableColumns(env.DB, "designs"),
      tableColumns(env.DB, "calculations")
    ]);

    const statements = [];

    function deleteCustomerRows(table, cols) {
      if (!cols.has("customer_id")) return;

      statements.push(
        env.DB.prepare(
          `DELETE FROM ${safeCol(table)}
           WHERE owner_id = ?
           AND customer_id IN (${cp})`
        ).bind(user.id, ...customerIdList)
      );
    }

    deleteCustomerRows("payments", paymentCols);
    deleteCustomerRows("alterations", alterationCols);
    deleteCustomerRows("measurements", measurementCols);
    deleteCustomerRows("designs", designCols);
    deleteCustomerRows("calculations", calculationCols);

    /*
     * Delete order-linked records.
     */
    if (orderIds.length) {
      const op = orderIds.map(() => "?").join(",");

      if (paymentCols.has("order_id")) {
        statements.push(
          env.DB.prepare(
            `DELETE FROM payments
             WHERE owner_id = ?
             AND order_id IN (${op})`
          ).bind(user.id, ...orderIds)
        );
      }

      if (alterationCols.has("order_id")) {
        statements.push(
          env.DB.prepare(
            `DELETE FROM alterations
             WHERE owner_id = ?
             AND order_id IN (${op})`
          ).bind(user.id, ...orderIds)
        );
      }

      if (calculationCols.has("order_id")) {
        statements.push(
          env.DB.prepare(
            `DELETE FROM calculations
             WHERE owner_id = ?
             AND order_id IN (${op})`
          ).bind(user.id, ...orderIds)
        );
      }
    }

    /*
     * Delete orders.
     */
    if (orderCustomerCols.length) {
      const links = orderCustomerCols
        .map(c => `${safeCol(c)} IN (${cp})`)
        .join(" OR ");

      statements.push(
        env.DB.prepare(
          `DELETE FROM orders
           WHERE owner_id = ?
           AND (${links})`
        ).bind(user.id, ...customerIdList)
      );
    }

    /*
     * FINAL: delete Main + Additional customers.
     */
    statements.push(
      env.DB.prepare(
        `DELETE FROM customers
         WHERE owner_id = ?
         AND id IN (${cp})`
      ).bind(user.id, ...customerIdList)
    );

    await env.DB.batch(statements);

    /*
     * HARD VERIFICATION
     */
    const remaining = {};

    const customerCheck = await env.DB.prepare(
      `SELECT COUNT(*) AS n
       FROM customers
       WHERE owner_id = ?
       AND id IN (${cp})`
    ).bind(user.id, ...customerIdList).first();

    remaining.customers = Number(customerCheck?.n || 0);

    if (orderCustomerCols.length) {
      const links = orderCustomerCols
        .map(c => `${safeCol(c)} IN (${cp})`)
        .join(" OR ");

      const r = await env.DB.prepare(
        `SELECT COUNT(*) AS n
         FROM orders
         WHERE owner_id = ?
         AND (${links})`
      ).bind(user.id, ...customerIdList).first();

      remaining.orders = Number(r?.n || 0);
    } else {
      remaining.orders = 0;
    }

    async function verifyCustomerTable(table, cols, key) {
      if (!cols.has("customer_id")) {
        remaining[key] = 0;
        return;
      }

      const r = await env.DB.prepare(
        `SELECT COUNT(*) AS n
         FROM ${safeCol(table)}
         WHERE owner_id = ?
         AND customer_id IN (${cp})`
      ).bind(user.id, ...customerIdList).first();

      remaining[key] = Number(r?.n || 0);
    }

    await verifyCustomerTable(
      "measurements", measurementCols, "measurements"
    );

    await verifyCustomerTable(
      "alterations", alterationCols, "alterations"
    );

    await verifyCustomerTable(
      "payments", paymentCols, "payments"
    );

    await verifyCustomerTable(
      "designs", designCols, "designs"
    );

    await verifyCustomerTable(
      "calculations", calculationCols, "calculations"
    );

    for (const [key, value] of Object.entries(remaining)) {
      if (Number(value) !== 0) {
        return json({
          ok: false,
          d1_verified: false,
          customer_ids_attempted: customerIdList,
          remaining,
          error: `Delete verification failed: ${key}=${value}`
        }, 500);
      }
    }

    return json({
      ok: true,
      d1_verified: true,
      customer_type: isAdditional ? "additional" : "main",
      customer_ids_deleted: customerIdList,
      customers_deleted: customerIdList.length,
      order_ids_deleted: orderIds,
      orders_deleted: orderIds.length,
      remaining
    });

  } catch (e) {
    return json({
      ok: false,
      d1_verified: false,
      error: String(e?.message || e)
    }, 500);
  }
}
