const SUPABASE_URL = "https://pdlsicwzfmartkwcwozx.supabase.co";
const SUPABASE_ANON_KEY = "sb_publishable_eCKne6Tzs6-QYSXpJb-aSQ_6AHe8n9O";

const TABLES = new Set([
  "customers",
  "orders",
  "measurements",
  "alterations",
  "payments",
  "designs",
  "calculations"
]);

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    const corsHeaders = cors();

    if (request.method === "OPTIONS") {
      return new Response("", { status: 204, headers: corsHeaders });
    }

    if (url.pathname === "/api/db" && request.method === "POST") {
      const auth = request.headers.get("Authorization") || "";
      const token = auth.startsWith("Bearer ") ? auth.slice(7) : "";
      if (!token) return json({ error: "Authentication required" }, 401);

      const user = await verifySupabaseUser(token);
      if (!user?.id) return json({ error: "Invalid session" }, 401);

      let body;
      try { body = await request.json(); }
      catch { return json({ error: "Invalid JSON" }, 400); }

      const table = String(body.table || "");
      if (!TABLES.has(table)) return json({ error: "Invalid table" }, 400);

      try {
        const op = String(body.operation || "select");
        const filters = Array.isArray(body.filters) ? body.filters : [];

        if (op === "select") {
          let sql = `SELECT * FROM ${safeCol(table)} WHERE owner_id = ?`;
          const args = [user.id];
          addFilters(filters, (s, a) => { sql += s; args.push(...a); });

          if (body.order?.field) {
            sql += ` ORDER BY ${safeCol(body.order.field)} ${body.order.ascending === false ? "DESC" : "ASC"}`;
          }

          const r = await env.DB.prepare(sql).bind(...args).all();
          const rows = r.results || [];

          if (body.single === "single") {
            if (rows.length !== 1) {
              return json({ data: null, error: rows.length ? "Multiple rows returned" : "No rows found" });
            }
            return json({ data: rows[0], error: null });
          }

          return json({
            data: body.single === "maybe" ? (rows[0] || null) : rows,
            error: null
          });
        }

        if (op === "insert") {
          const input = Array.isArray(body.payload) ? body.payload : [body.payload];
          const out = [];

          for (const raw of input) {
            const row = { ...(raw || {}) };
            row.id ||= crypto.randomUUID();
            row.owner_id = user.id;
            row.created_at ||= new Date().toISOString();
            row.updated_at ||= row.created_at;
            await insertRow(env.DB, table, row);
            out.push(row);
          }

          return json({ data: body.single ? (out[0] || null) : out, error: null });
        }

        if (op === "update") {
          const payload = { ...(body.payload || {}) };
          delete payload.id;
          delete payload.owner_id;

          const cols = Object.keys(payload).filter(validCol);
          if (!cols.length) return json({ data: [], error: null });

          let sql = `UPDATE ${safeCol(table)} SET ${cols.map(c => `${safeCol(c)} = ?`).join(", ")}, updated_at = ? WHERE owner_id = ?`;
          const args = cols.map(c => payload[c]);
          args.push(new Date().toISOString(), user.id);

          addFilters(filters, (s, a) => { sql += s; args.push(...a); });
          await env.DB.prepare(sql).bind(...args).run();

          return selectAfter(env.DB, table, user.id, filters, body.single);
        }

        if (op === "delete") {
          let sql = `DELETE FROM ${safeCol(table)} WHERE owner_id = ?`;
          const args = [user.id];
          addFilters(filters, (s, a) => { sql += s; args.push(...a); });
          await env.DB.prepare(sql).bind(...args).run();
          return json({ data: null, error: null });
        }

        return json({ error: "Unsupported operation" }, 400);
      } catch (e) {
        return json({ error: String(e?.message || e) }, 500);
      }
    }

    if (url.pathname === "/api/order-delete" && request.method === "POST") {
      const auth = request.headers.get("Authorization") || "";
      const token = auth.startsWith("Bearer ") ? auth.slice(7) : "";
      if (!token) return json({ error: "Authentication required" }, 401);

      const user = await verifySupabaseUser(token);
      if (!user?.id) return json({ error: "Invalid session" }, 401);

      let body;
      try { body = await request.json(); }
      catch { return json({ error: "Invalid JSON" }, 400); }

      const orderId = String(body.order_id || "").trim();
      if (!orderId) return json({ error: "order_id is required" }, 400);

      try {
        const orderCols = await tableColumns(env.DB, "orders");
        if (!orderCols.has("id")) return json({ error: "orders.id column missing" }, 500);

        const order = await env.DB.prepare(
          `SELECT * FROM orders WHERE id = ? AND owner_id = ? LIMIT 1`
        ).bind(orderId, user.id).first();

        if (!order) return json({ error: "Order not found" }, 404);

        const [paymentCols, alterationCols, calculationCols] = await Promise.all([
          tableColumns(env.DB, "payments"),
          tableColumns(env.DB, "alterations"),
          tableColumns(env.DB, "calculations")
        ]);

        const statements = [];
        if (paymentCols.has("order_id")) {
          statements.push(env.DB.prepare(
            `DELETE FROM payments WHERE owner_id = ? AND order_id = ?`
          ).bind(user.id, orderId));
        }
        if (alterationCols.has("order_id")) {
          statements.push(env.DB.prepare(
            `DELETE FROM alterations WHERE owner_id = ? AND order_id = ?`
          ).bind(user.id, orderId));
        }
        if (calculationCols.has("order_id")) {
          statements.push(env.DB.prepare(
            `DELETE FROM calculations WHERE owner_id = ? AND order_id = ?`
          ).bind(user.id, orderId));
        }

        statements.push(env.DB.prepare(
          `DELETE FROM orders WHERE owner_id = ? AND id = ?`
        ).bind(user.id, orderId));

        await env.DB.batch(statements);

        const remaining = {};
        const orderCheck = await env.DB.prepare(
          `SELECT COUNT(*) AS n FROM orders WHERE owner_id = ? AND id = ?`
        ).bind(user.id, orderId).first();
        remaining.orders = Number(orderCheck?.n || 0);

        async function verifyOrderTable(table, cols, key) {
          if (!cols.has("order_id")) return;
          const r = await env.DB.prepare(
            `SELECT COUNT(*) AS n FROM ${table} WHERE owner_id = ? AND order_id = ?`
          ).bind(user.id, orderId).first();
          remaining[key] = Number(r?.n || 0);
        }

        await verifyOrderTable("payments", paymentCols, "payments");
        await verifyOrderTable("alterations", alterationCols, "alterations");
        await verifyOrderTable("calculations", calculationCols, "calculations");

        for (const [key, value] of Object.entries(remaining)) {
          if (Number(value) !== 0) {
            throw new Error(`Order cascade verification failed: ${key}=${value}`);
          }
        }

        let r2ObjectsDeleted = 0;
        if (!env.MY_BUCKET) throw new Error("R2 bucket binding MY_BUCKET is missing");

        let cursor;
        do {
          const listed = await env.MY_BUCKET.list({
            prefix: `orders/${orderId}/`,
            limit: 1000,
            ...(cursor ? { cursor } : {})
          });

          const keys = (listed.objects || []).map(o => o.key).filter(Boolean);
          if (keys.length) {
            await env.MY_BUCKET.delete(keys);
            r2ObjectsDeleted += keys.length;
          }
          cursor = listed.truncated ? listed.cursor : undefined;
        } while (cursor);

        return json({
          ok: true,
          order_id: orderId,
          d1_verified: true,
          r2_objects_deleted: r2ObjectsDeleted,
          remaining
        });
      } catch (e) {
        return json({ ok: false, error: String(e?.message || e) }, 500);
      }
    }

    if (url.pathname === "/api/customer-delete" && request.method === "POST") {
      const auth = request.headers.get("Authorization") || "";
      const token = auth.startsWith("Bearer ") ? auth.slice(7) : "";
      if (!token) return json({ error: "Authentication required" }, 401);

      const user = await verifySupabaseUser(token);
      if (!user?.id) return json({ error: "Invalid session" }, 401);

      let body;
      try { body = await request.json(); }
      catch { return json({ error: "Invalid JSON" }, 400); }

      const customerId = String(body.customer_id || "").trim();
      if (!customerId) return json({ error: "customer_id is required" }, 400);

      try {
        const selected = await env.DB.prepare(
          `SELECT * FROM customers WHERE id = ? AND owner_id = ? LIMIT 1`
        ).bind(customerId, user.id).first();

        if (!selected) return json({ error: "Customer not found" }, 404);

        const allResult = await env.DB.prepare(
          `SELECT * FROM customers WHERE owner_id = ?`
        ).bind(user.id).all();

        const allCustomers = allResult.results || [];

        function parentIdOf(c) {
          if (!c) return "";
          const notes = String(c.notes || "");
          const marker = notes.match(/(?:^|\|\s*)(?:NF_MULTI_PARENT|NF_PARENT_CUSTOMER)\s*:\s*([^|\s]+)/i);
          if (marker?.[1]) return String(marker[1]).trim();
          return String(c.main_customer_id || c.parent_customer_id || c.parent_id || c.main_id || "").trim();
        }

        const selectedParent = parentIdOf(selected);
        const isAdditional = !!selectedParent;
        // Build the complete Multi-Customer family recursively.
        // Main delete => Main + all descendants.
        // Additional delete => selected Additional + its descendants only.
        const customerIds = new Set([customerId]);

        let changed = true;
        while (changed) {
          changed = false;
          for (const c of allCustomers) {
            const cid = String(c?.id || "").trim();
            if (!cid || customerIds.has(cid)) continue;

            const parent = parentIdOf(c);
            if (customerIds.has(parent)) {
              customerIds.add(cid);
              changed = true;
            }
          }
        }

        const customerIdList = Array.from(customerIds);
        const cp = customerIdList.map(() => "?").join(",");

        // Find orders using every supported customer-reference column.
        // This is intentionally broader than only orders.customer_id because
        // older data may use customerId/cust_id/client_id style fields.
        const orderCols = await tableColumns(env.DB, "orders");
        const orderCustomerCols = [
          "customer_id", "customerId",
          "cust_id", "custId",
          "client_id", "clientId"
        ].filter(c => orderCols.has(c));

        let orderResult = { results: [] };

        if (orderCustomerCols.length) {
          const orderLinks = orderCustomerCols
            .map(c => `${safeCol(c)} IN (${cp})`)
            .join(" OR ");

          orderResult = await env.DB.prepare(
            `SELECT id FROM orders WHERE owner_id = ? AND (${orderLinks})`
          ).bind(user.id, ...customerIdList).all();
        }

        const orderIds = (orderResult.results || [])
          .map(r => String(r.id || "").trim())
          .filter(Boolean);

        const [paymentCols, alterationCols, measurementCols, designCols, calculationCols] =
          await Promise.all([
            tableColumns(env.DB, "payments"),
            tableColumns(env.DB, "alterations"),
            tableColumns(env.DB, "measurements"),
            tableColumns(env.DB, "designs"),
            tableColumns(env.DB, "calculations")
          ]);

        const statements = [];

        function deleteByCustomer(table, cols) {
          if (!cols.has("customer_id")) return;
          statements.push(
            env.DB.prepare(
              `DELETE FROM ${table} WHERE owner_id = ? AND customer_id IN (${cp})`
            ).bind(user.id, ...customerIdList)
          );
        }

        deleteByCustomer("payments", paymentCols);
        deleteByCustomer("alterations", alterationCols);
        deleteByCustomer("measurements", measurementCols);
        deleteByCustomer("designs", designCols);
        deleteByCustomer("calculations", calculationCols);

        if (orderIds.length) {
          const op = orderIds.map(() => "?").join(",");

          if (paymentCols.has("order_id")) {
            statements.push(env.DB.prepare(
              `DELETE FROM payments WHERE owner_id = ? AND order_id IN (${op})`
            ).bind(user.id, ...orderIds));
          }

          if (alterationCols.has("order_id")) {
            statements.push(env.DB.prepare(
              `DELETE FROM alterations WHERE owner_id = ? AND order_id IN (${op})`
            ).bind(user.id, ...orderIds));
          }

          if (calculationCols.has("order_id")) {
            statements.push(env.DB.prepare(
              `DELETE FROM calculations WHERE owner_id = ? AND order_id IN (${op})`
            ).bind(user.id, ...orderIds));
          }
        }

        if (orderCustomerCols.length) {
          const orderLinks = orderCustomerCols
            .map(c => `${safeCol(c)} IN (${cp})`)
            .join(" OR ");

          statements.push(
            env.DB.prepare(
              `DELETE FROM orders WHERE owner_id = ? AND (${orderLinks})`
            ).bind(user.id, ...customerIdList)
          );
        }

        statements.push(
          env.DB.prepare(
            `DELETE FROM customers WHERE owner_id = ? AND id IN (${cp})`
          ).bind(user.id, ...customerIdList)
        );

        if (statements.length) await env.DB.batch(statements);

        const remaining = {};

        const customerCheck = await env.DB.prepare(
          `SELECT COUNT(*) AS n FROM customers WHERE owner_id = ? AND id IN (${cp})`
        ).bind(user.id, ...customerIdList).first();
        remaining.customers = Number(customerCheck?.n || 0);

        if (orderCustomerCols.length) {
          const orderLinks = orderCustomerCols
            .map(c => `${safeCol(c)} IN (${cp})`)
            .join(" OR ");

          const orderCheck = await env.DB.prepare(
            `SELECT COUNT(*) AS n FROM orders WHERE owner_id = ? AND (${orderLinks})`
          ).bind(user.id, ...customerIdList).first();

          remaining.orders = Number(orderCheck?.n || 0);
        } else {
          remaining.orders = 0;
        }

        async function verifyCustomerTable(table, cols, key) {
          if (!cols.has("customer_id")) return;
          const r = await env.DB.prepare(
            `SELECT COUNT(*) AS n FROM ${table} WHERE owner_id = ? AND customer_id IN (${cp})`
          ).bind(user.id, ...customerIdList).first();
          remaining[key] = Number(r?.n || 0);
        }

        await verifyCustomerTable("measurements", measurementCols, "measurements");
        await verifyCustomerTable("alterations", alterationCols, "alterations");
        await verifyCustomerTable("payments", paymentCols, "payments");
        await verifyCustomerTable("designs", designCols, "designs");
        await verifyCustomerTable("calculations", calculationCols, "calculations");

        if (orderIds.length) {
          const op = orderIds.map(() => "?").join(",");

          async function verifyOrderTable(table, cols, key) {
            if (!cols.has("order_id")) return;
            const r = await env.DB.prepare(
              `SELECT COUNT(*) AS n FROM ${table} WHERE owner_id = ? AND order_id IN (${op})`
            ).bind(user.id, ...orderIds).first();
            remaining[key] = Number(r?.n || 0);
          }

          await verifyOrderTable("payments", paymentCols, "payments_orders");
          await verifyOrderTable("alterations", alterationCols, "alterations_orders");
          await verifyOrderTable("calculations", calculationCols, "calculations_orders");
        }

        for (const [key, value] of Object.entries(remaining)) {
          if (Number(value) !== 0) {
            throw new Error(`Customer cascade verification failed: ${key}=${value}`);
          }
        }

        const prefixes = new Set();
        for (const id of customerIdList) {
          prefixes.add(`${id}/`);
          prefixes.add(`customers/${id}/`);
        }
        for (const oid of orderIds) prefixes.add(`orders/${oid}/`);

        let deletedObjects = 0;

        if (!env.MY_BUCKET) {
          throw new Error("R2 bucket binding MY_BUCKET is missing");
        }

        for (const prefix of prefixes) {
          let cursor;
          do {
            const listed = await env.MY_BUCKET.list({
              prefix,
              limit: 1000,
              ...(cursor ? { cursor } : {})
            });

            const keys = (listed.objects || [])
              .map(o => o.key)
              .filter(Boolean);

            if (keys.length) {
              await env.MY_BUCKET.delete(keys);
              deletedObjects += keys.length;
            }

            cursor = listed.truncated ? listed.cursor : undefined;
          } while (cursor);
        }

        return json({
          ok: true,
          customer_id: customerId,
          customer_type: isAdditional ? "additional" : "main",
          customer_ids_deleted: customerIdList,
          customers_deleted: customerIdList.length,
          order_ids: orderIds,
          orders_deleted: orderIds.length,
          d1_verified: true,
          r2_objects_deleted: deletedObjects,
          remaining
        });
      } catch (e) {
        return json({
          ok: false,
          error: String(e?.message || e)
        }, 500);
      }
    }

    if (url.pathname === "/api/r2-test") {
      await env.MY_BUCKET.put("r2-test.txt", "R2 WORKS", {
        httpMetadata: { contentType: "text/plain" }
      });
      return new Response("R2 upload OK", { headers: corsHeaders });
    }

    if (url.pathname.startsWith("/api/r2/") && request.method === "PUT") {
      const key = decodeURIComponent(url.pathname.slice("/api/r2/".length));
      if (!key) return new Response("Missing file name", { status: 400, headers: corsHeaders });

      await env.MY_BUCKET.put(key, request.body, {
        httpMetadata: {
          contentType: request.headers.get("content-type") || "application/octet-stream"
        }
      });

      return json({ ok: true, key });
    }

    if (url.pathname.startsWith("/api/r2/") && request.method === "GET") {
      const key = decodeURIComponent(url.pathname.slice("/api/r2/".length));
      const object = await env.MY_BUCKET.get(key);

      if (!object) {
        return new Response("File not found", {
          status: 404,
          headers: corsHeaders
        });
      }

      const headers = new Headers(corsHeaders);
      headers.set("Content-Type", object.httpMetadata?.contentType || "application/octet-stream");
      headers.set("Cache-Control", "no-store");

      return new Response(object.body, { headers });
    }

    if (url.pathname.startsWith("/api/r2/") && request.method === "DELETE") {
      const key = decodeURIComponent(url.pathname.slice("/api/r2/".length));
      await env.MY_BUCKET.delete(key);
      return json({ ok: true, key });
    }

    if (env.ASSETS?.fetch) {
      return env.ASSETS.fetch(request);
    }

    return new Response("Not found", {
      status: 404,
      headers: corsHeaders
    });
  }
};

function validCol(c) {
  return /^[A-Za-z_][A-Za-z0-9_]*$/.test(String(c || ""));
}

function safeCol(c) {
  if (!validCol(c)) throw new Error("Invalid column");
  return String(c);
}

function addFilters(filters, add) {
  for (const f of filters) {
    if (!f || typeof f !== "object") continue;

    const field = safeCol(f.field);

    if (Array.isArray(f.__in)) {
      if (!f.__in.length) {
        add(" AND 0", []);
        continue;
      }

      add(
        ` AND ${field} IN (${f.__in.map(() => "?").join(",")})`,
        f.__in
      );
      continue;
    }

    if (Object.prototype.hasOwnProperty.call(f, "__gt")) {
      add(` AND ${field} > ?`, [f.__gt]);
      continue;
    }

    add(` AND ${field} = ?`, [f.value]);
  }
}

async function tableColumns(db, table) {
  if (!TABLES.has(table)) {
    throw new Error("Invalid table");
  }

  const r = await db
    .prepare(`PRAGMA table_info(${safeCol(table)})`)
    .all();

  return new Set(
    (r.results || [])
      .map(x => String(x.name || ""))
      .filter(Boolean)
  );
}

async function insertRow(db, table, row) {
  const cols = Object.keys(row).filter(validCol);
  if (!cols.length) throw new Error("No valid columns");

  const sql =
    `INSERT INTO ${safeCol(table)} (${cols.join(",")}) ` +
    `VALUES (${cols.map(() => "?").join(",")})`;

  await db.prepare(sql).bind(...cols.map(c => row[c])).run();
}

async function selectAfter(db, table, owner, filters, single) {
  let sql = `SELECT * FROM ${safeCol(table)} WHERE owner_id = ?`;
  const args = [owner];

  addFilters(filters, (s, a) => {
    sql += s;
    args.push(...a);
  });

  const r = await db.prepare(sql).bind(...args).all();
  const rows = r.results || [];

  if (single === "single") {
    if (rows.length !== 1) {
      return json({
        data: null,
        error: rows.length ? "Multiple rows returned" : "No rows found"
      });
    }

    return json({ data: rows[0], error: null });
  }

  return json({
    data: single === "maybe" ? (rows[0] || null) : rows,
    error: null
  });
}

async function verifySupabaseUser(token) {
  try {
    const r = await fetch(`${SUPABASE_URL}/auth/v1/user`, {
      headers: {
        apikey: SUPABASE_ANON_KEY,
        Authorization: `Bearer ${token}`
      }
    });

    if (!r.ok) return null;
    return await r.json();
  } catch {
    return null;
  }
}

function cors() {
  return {
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Headers": "Authorization,Content-Type",
    "Access-Control-Allow-Methods": "GET,PUT,DELETE,POST,OPTIONS",
    "Content-Type": "application/json"
  };
}

function json(data, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: cors()
  });
}
