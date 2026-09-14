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
      return new Response("", {
        status: 204,
        headers: corsHeaders
      });
    }

    /* =====================================================
       GENERIC D1 API
       ===================================================== */

    if (url.pathname === "/api/db" && request.method === "POST") {
      const auth = request.headers.get("Authorization") || "";
      const token = auth.startsWith("Bearer ")
        ? auth.slice(7)
        : "";

      if (!token) {
        return json({
          error: "Authentication required"
        }, 401);
      }

      const user = await verifySupabaseUser(token);

      if (!user?.id) {
        return json({
          error: "Invalid session"
        }, 401);
      }

      let body;

      try {
        body = await request.json();
      } catch {
        return json({
          error: "Invalid JSON"
        }, 400);
      }

      const table = String(body.table || "");

      if (!TABLES.has(table)) {
        return json({
          error: "Invalid table"
        }, 400);
      }

      try {
        const op = String(
          body.operation || "select"
        );

        const filters = Array.isArray(body.filters)
          ? body.filters
          : [];

        /* SELECT */

        if (op === "select") {
          let sql =
            `SELECT * FROM ${safeCol(table)} ` +
            `WHERE owner_id = ?`;

          const args = [user.id];

          addFilters(filters, (s, a) => {
            sql += s;
            args.push(...a);
          });

          if (body.order?.field) {
            sql +=
              ` ORDER BY ${safeCol(body.order.field)} ` +
              `${body.order.ascending === false
                ? "DESC"
                : "ASC"}`;
          }

          const r = await env.DB
            .prepare(sql)
            .bind(...args)
            .all();

          const rows = r.results || [];

          if (body.single === "single") {
            if (rows.length !== 1) {
              return json({
                data: null,
                error: rows.length
                  ? "Multiple rows returned"
                  : "No rows found"
              });
            }

            return json({
              data: rows[0],
              error: null
            });
          }

          return json({
            data:
              body.single === "maybe"
                ? (rows[0] || null)
                : rows,
            error: null
          });
        }

        /* INSERT */

        if (op === "insert") {
          const input = Array.isArray(body.payload)
            ? body.payload
            : [body.payload];

          const out = [];

          for (const raw of input) {
            const row = {
              ...(raw || {})
            };

            row.id ||= crypto.randomUUID();
            row.owner_id = user.id;
            row.created_at ||=
              new Date().toISOString();
            row.updated_at ||=
              row.created_at;

            await insertRow(
              env.DB,
              table,
              row
            );

            out.push(row);
          }

          return json({
            data: body.single
              ? (out[0] || null)
              : out,
            error: null
          });
        }

        /* UPDATE */

        if (op === "update") {
          const payload = {
            ...(body.payload || {})
          };

          delete payload.id;
          delete payload.owner_id;

          const cols = Object.keys(payload)
            .filter(validCol);

          if (!cols.length) {
            return json({
              data: [],
              error: null
            });
          }

          let sql =
            `UPDATE ${safeCol(table)} SET ` +
            `${cols
              .map(c => `${safeCol(c)} = ?`)
              .join(", ")}, ` +
            `updated_at = ? ` +
            `WHERE owner_id = ?`;

          const args =
            cols.map(c => payload[c]);

          args.push(
            new Date().toISOString(),
            user.id
          );

          addFilters(filters, (s, a) => {
            sql += s;
            args.push(...a);
          });

          await env.DB
            .prepare(sql)
            .bind(...args)
            .run();

          return selectAfter(
            env.DB,
            table,
            user.id,
            filters,
            body.single
          );
        }

        /* DELETE */

        if (op === "delete") {
          let sql =
            `DELETE FROM ${safeCol(table)} ` +
            `WHERE owner_id = ?`;

          const args = [user.id];

          addFilters(filters, (s, a) => {
            sql += s;
            args.push(...a);
          });

          await env.DB
            .prepare(sql)
            .bind(...args)
            .run();

          return json({
            data: null,
            error: null
          });
        }

        return json({
          error: "Unsupported operation"
        }, 400);

      } catch (e) {
        return json({
          error: String(e?.message || e)
        }, 500);
      }
    }


    /* =====================================================
       UNIFIED CUSTOMER CASCADE DELETE
       
       MAIN:
         Main + Additional Customers

       ADDITIONAL:
         Additional only

       D1:
         customers
         orders
         payments
         alterations
         measurements
         designs
         calculations

       R2:
         customer prefixes
         order prefixes
       ===================================================== */

    if (
      url.pathname === "/api/customer-delete" &&
      request.method === "POST"
    ) {
      const auth =
        request.headers.get("Authorization") || "";

      const token =
        auth.startsWith("Bearer ")
          ? auth.slice(7)
          : "";

      if (!token) {
        return json({
          error: "Authentication required"
        }, 401);
      }

      const user =
        await verifySupabaseUser(token);

      if (!user?.id) {
        return json({
          error: "Invalid session"
        }, 401);
      }

      let body;

      try {
        body = await request.json();
      } catch {
        return json({
          error: "Invalid JSON"
        }, 400);
      }

      const customerId =
        String(
          body.customer_id || ""
        ).trim();

      if (!customerId) {
        return json({
          error: "customer_id is required"
        }, 400);
      }

      try {
        /* -------------------------------------------------
           FIND SELECTED CUSTOMER
           ------------------------------------------------- */

        const customer =
          await env.DB
            .prepare(
              `SELECT id, name, notes
               FROM customers
               WHERE id = ?
                 AND owner_id = ?
               LIMIT 1`
            )
            .bind(
              customerId,
              user.id
            )
            .first();

        if (!customer) {
          return json({
            error: "Customer not found"
          }, 404);
        }

        const selectedNotes =
          String(
            customer.notes || ""
          );

        const isAdditionalCustomer =
          selectedNotes.includes(
            "NF_MULTI_PARENT:"
          );

        /* -------------------------------------------------
           BUILD CUSTOMER CASCADE SET
           ------------------------------------------------- */

        const customerIds =
          new Set([customerId]);

        /*
         * If selected customer is MAIN,
         * collect all linked Additional Customers.
         */
        if (!isAdditionalCustomer) {
          const childResult =
            await env.DB
              .prepare(
                `SELECT id
                 FROM customers
                 WHERE owner_id = ?
                   AND notes LIKE ?`
              )
              .bind(
                user.id,
                `%NF_MULTI_PARENT:${customerId}%`
              )
              .all();

          for (
            const child
              of (childResult.results || [])
          ) {
            const childId =
              String(
                child.id || ""
              ).trim();

            if (childId) {
              customerIds.add(childId);
            }
          }
        }

        const deleteCustomerIds =
          Array.from(customerIds);

        if (!deleteCustomerIds.length) {
          throw new Error(
            "No customer IDs found for deletion"
          );
        }

        const customerPlaceholders =
          deleteCustomerIds
            .map(() => "?")
            .join(",");

        /* -------------------------------------------------
           FIND ALL ORDERS
           ------------------------------------------------- */

        const orderResult =
          await env.DB
            .prepare(
              `SELECT id
               FROM orders
               WHERE owner_id = ?
                 AND customer_id IN
                   (${customerPlaceholders})`
            )
            .bind(
              user.id,
              ...deleteCustomerIds
            )
            .all();

        const orderIds =
          (orderResult.results || [])
            .map(r =>
              String(r.id || "").trim()
            )
            .filter(Boolean);

        /* -------------------------------------------------
           GET ACTUAL TABLE COLUMNS
           ------------------------------------------------- */

        const [
          paymentCols,
          alterationCols,
          measurementCols,
          designCols,
          calculationCols
        ] = await Promise.all([
          tableColumns(
            env.DB,
            "payments"
          ),
          tableColumns(
            env.DB,
            "alterations"
          ),
          tableColumns(
            env.DB,
            "measurements"
          ),
          tableColumns(
            env.DB,
            "designs"
          ),
          tableColumns(
            env.DB,
            "calculations"
          )
        ]);

        const statements = [];

        /* -------------------------------------------------
           PAYMENTS BY CUSTOMER
           ------------------------------------------------- */

        if (
          paymentCols.has("customer_id")
        ) {
          statements.push(
            env.DB.prepare(
              `DELETE FROM payments
               WHERE owner_id = ?
                 AND customer_id IN
                   (${customerPlaceholders})`
            ).bind(
              user.id,
              ...deleteCustomerIds
            )
          );
        }

        /* -------------------------------------------------
           ALTERATIONS BY CUSTOMER
           ------------------------------------------------- */

        if (
          alterationCols.has("customer_id")
        ) {
          statements.push(
            env.DB.prepare(
              `DELETE FROM alterations
               WHERE owner_id = ?
                 AND customer_id IN
                   (${customerPlaceholders})`
            ).bind(
              user.id,
              ...deleteCustomerIds
            )
          );
        }

        /* -------------------------------------------------
           MEASUREMENTS BY CUSTOMER
           ------------------------------------------------- */

        if (
          measurementCols.has("customer_id")
        ) {
          statements.push(
            env.DB.prepare(
              `DELETE FROM measurements
               WHERE owner_id = ?
                 AND customer_id IN
                   (${customerPlaceholders})`
            ).bind(
              user.id,
              ...deleteCustomerIds
            )
          );
        }

        /* -------------------------------------------------
           DESIGNS BY CUSTOMER
           ------------------------------------------------- */

        if (
          designCols.has("customer_id")
        ) {
          statements.push(
            env.DB.prepare(
              `DELETE FROM designs
               WHERE owner_id = ?
                 AND customer_id IN
                   (${customerPlaceholders})`
            ).bind(
              user.id,
              ...deleteCustomerIds
            )
          );
        }

        /* -------------------------------------------------
           CALCULATIONS BY CUSTOMER
           ------------------------------------------------- */

        if (
          calculationCols.has("customer_id")
        ) {
          statements.push(
            env.DB.prepare(
              `DELETE FROM calculations
               WHERE owner_id = ?
                 AND customer_id IN
                   (${customerPlaceholders})`
            ).bind(
              user.id,
              ...deleteCustomerIds
            )
          );
        }

        /* -------------------------------------------------
           ORDER-LINKED DATA
           ------------------------------------------------- */

        if (orderIds.length) {
          const orderPlaceholders =
            orderIds
              .map(() => "?")
              .join(",");

          /* PAYMENTS BY ORDER */

          if (
            paymentCols.has("order_id")
          ) {
            statements.push(
              env.DB.prepare(
                `DELETE FROM payments
                 WHERE owner_id = ?
                   AND order_id IN
                     (${orderPlaceholders})`
              ).bind(
                user.id,
                ...orderIds
              )
            );
          }

          /* ALTERATIONS BY ORDER */

          if (
            alterationCols.has("order_id")
          ) {
            statements.push(
              env.DB.prepare(
                `DELETE FROM alterations
                 WHERE owner_id = ?
                   AND order_id IN
                     (${orderPlaceholders})`
              ).bind(
                user.id,
                ...orderIds
              )
            );
          }

          /* CALCULATIONS BY ORDER */

          if (
            calculationCols.has("order_id")
          ) {
            statements.push(
              env.DB.prepare(
                `DELETE FROM calculations
                 WHERE owner_id = ?
                   AND order_id IN
                     (${orderPlaceholders})`
              ).bind(
                user.id,
                ...orderIds
              )
            );
          }
        }

        /* -------------------------------------------------
           DELETE ORDERS
           ------------------------------------------------- */

        statements.push(
          env.DB.prepare(
            `DELETE FROM orders
             WHERE owner_id = ?
               AND customer_id IN
                 (${customerPlaceholders})`
          ).bind(
            user.id,
            ...deleteCustomerIds
          )
        );

        /* -------------------------------------------------
           DELETE CUSTOMERS
           ------------------------------------------------- */

        statements.push(
          env.DB.prepare(
            `DELETE FROM customers
             WHERE owner_id = ?
               AND id IN
                 (${customerPlaceholders})`
          ).bind(
            user.id,
            ...deleteCustomerIds
          )
        );

        /* -------------------------------------------------
           EXECUTE D1 CASCADE
           ------------------------------------------------- */

        await env.DB.batch(
          statements
        );

        /* -------------------------------------------------
           VERIFY CUSTOMERS
           ------------------------------------------------- */

        const customerCheck =
          await env.DB
            .prepare(
              `SELECT id
               FROM customers
               WHERE owner_id = ?
                 AND id IN
                   (${customerPlaceholders})`
            )
            .bind(
              user.id,
              ...deleteCustomerIds
            )
            .all();

        if (
          (customerCheck.results || [])
            .length
        ) {
          throw new Error(
            "Customer D1 delete failed: customer row still exists"
          );
        }

        const remaining = {};

        /* -------------------------------------------------
           VERIFY ORDERS
           ------------------------------------------------- */

        const orderCheck =
          await env.DB
            .prepare(
              `SELECT COUNT(*) AS n
               FROM orders
               WHERE owner_id = ?
                 AND customer_id IN
                   (${customerPlaceholders})`
            )
            .bind(
              user.id,
              ...deleteCustomerIds
            )
            .first();

        remaining.orders =
          Number(
            orderCheck?.n || 0
          );

        if (
          remaining.orders !== 0
        ) {
          throw new Error(
            `Customer cascade verification failed: orders=${remaining.orders}`
          );
        }

        /* -------------------------------------------------
           VERIFY MEASUREMENTS
           ------------------------------------------------- */

        if (
          measurementCols.has("customer_id")
        ) {
          const r =
            await env.DB
              .prepare(
                `SELECT COUNT(*) AS n
                 FROM measurements
                 WHERE owner_id = ?
                   AND customer_id IN
                     (${customerPlaceholders})`
              )
              .bind(
                user.id,
                ...deleteCustomerIds
              )
              .first();

          remaining.measurements =
            Number(r?.n || 0);

          if (
            remaining.measurements !== 0
          ) {
            throw new Error(
              `Customer cascade verification failed: measurements=${remaining.measurements}`
            );
          }
        }

        /* -------------------------------------------------
           VERIFY ALTERATIONS BY CUSTOMER
           ------------------------------------------------- */

        if (
          alterationCols.has("customer_id")
        ) {
          const r =
            await env.DB
              .prepare(
                `SELECT COUNT(*) AS n
                 FROM alterations
                 WHERE owner_id = ?
                   AND customer_id IN
                     (${customerPlaceholders})`
              )
              .bind(
                user.id,
                ...deleteCustomerIds
              )
              .first();

          remaining.alterations_customer =
            Number(r?.n || 0);

          if (
            remaining.alterations_customer !== 0
          ) {
            throw new Error(
              `Customer cascade verification failed: alterations_customer=${remaining.alterations_customer}`
            );
          }
        }

        /* -------------------------------------------------
           VERIFY PAYMENTS BY CUSTOMER
           ------------------------------------------------- */

        if (
          paymentCols.has("customer_id")
        ) {
          const r =
            await env.DB
              .prepare(
                `SELECT COUNT(*) AS n
                 FROM payments
                 WHERE owner_id = ?
                   AND customer_id IN
                     (${customerPlaceholders})`
              )
              .bind(
                user.id,
                ...deleteCustomerIds
              )
              .first();

          remaining.payments_customer =
            Number(r?.n || 0);

          if (
            remaining.payments_customer !== 0
          ) {
            throw new Error(
              `Customer cascade verification failed: payments_customer=${remaining.payments_customer}`
            );
          }
        }

        /* -------------------------------------------------
           VERIFY DESIGNS
           ------------------------------------------------- */

        if (
          designCols.has("customer_id")
        ) {
          const r =
            await env.DB
              .prepare(
                `SELECT COUNT(*) AS n
                 FROM designs
                 WHERE owner_id = ?
                   AND customer_id IN
                     (${customerPlaceholders})`
              )
              .bind(
                user.id,
                ...deleteCustomerIds
              )
              .first();

          remaining.designs_customer =
            Number(r?.n || 0);

          if (
            remaining.designs_customer !== 0
          ) {
            throw new Error(
              `Customer cascade verification failed: designs_customer=${remaining.designs_customer}`
            );
          }
        }

        /* -------------------------------------------------
           VERIFY CALCULATIONS BY CUSTOMER
           ------------------------------------------------- */

        if (
          calculationCols.has("customer_id")
        ) {
          const r =
            await env.DB
              .prepare(
                `SELECT COUNT(*) AS n
                 FROM calculations
                 WHERE owner_id = ?
                   AND customer_id IN
                     (${customerPlaceholders})`
              )
              .bind(
                user.id,
                ...deleteCustomerIds
              )
              .first();

          remaining.calculations_customer =
            Number(r?.n || 0);

          if (
            remaining.calculations_customer !== 0
          ) {
            throw new Error(
              `Customer cascade verification failed: calculations_customer=${remaining.calculations_customer}`
            );
          }
        }

        /* -------------------------------------------------
           VERIFY PAYMENTS BY ORDER
           ------------------------------------------------- */

        if (
          orderIds.length &&
          paymentCols.has("order_id")
        ) {
          const orderPlaceholders =
            orderIds
              .map(() => "?")
              .join(",");

          const r =
            await env.DB
              .prepare(
                `SELECT COUNT(*) AS n
                 FROM payments
                 WHERE owner_id = ?
                   AND order_id IN
                     (${orderPlaceholders})`
              )
              .bind(
                user.id,
                ...orderIds
              )
              .first();

          remaining.payments_orders =
            Number(r?.n || 0);

          if (
            remaining.payments_orders !== 0
          ) {
            throw new Error(
              `Customer cascade verification failed: payments_orders=${remaining.payments_orders}`
            );
          }
        }

        /* -------------------------------------------------
           VERIFY ALTERATIONS BY ORDER
           ------------------------------------------------- */

        if (
          orderIds.length &&
          alterationCols.has("order_id")
        ) {
          const orderPlaceholders =
            orderIds
              .map(() => "?")
              .join(",");

          const r =
            await env.DB
              .prepare(
                `SELECT COUNT(*) AS n
                 FROM alterations
                 WHERE owner_id = ?
                   AND order_id IN
                     (${orderPlaceholders})`
              )
              .bind(
                user.id,
                ...orderIds
              )
              .first();

          remaining.alterations_orders =
            Number(r?.n || 0);

          if (
            remaining.alterations_orders !== 0
          ) {
            throw new Error(
              `Customer cascade verification failed: alterations_orders=${remaining.alterations_orders}`
            );
          }
        }

        /* -------------------------------------------------
           VERIFY CALCULATIONS BY ORDER
           ------------------------------------------------- */

        if (
          orderIds.length &&
          calculationCols.has("order_id")
        ) {
          const orderPlaceholders =
            orderIds
              .map(() => "?")
              .join(",");

          const r =
            await env.DB
              .prepare(
                `SELECT COUNT(*) AS n
                 FROM calculations
                 WHERE owner_id = ?
                   AND order_id IN
                     (${orderPlaceholders})`
              )
              .bind(
                user.id,
                ...orderIds
              )
              .first();

          remaining.calculations_orders =
            Number(r?.n || 0);

          if (
            remaining.calculations_orders !== 0
          ) {
            throw new Error(
              `Customer cascade verification failed: calculations_orders=${remaining.calculations_orders}`
            );
          }
        }

        /* -------------------------------------------------
           R2 CLEANUP
           ------------------------------------------------- */

        const prefixes =
          new Set();

        for (
          const id
            of deleteCustomerIds
        ) {
          prefixes.add(`${id}/`);
          prefixes.add(
            `customers/${id}/`
          );
        }

        for (
          const orderId
            of orderIds
        ) {
          prefixes.add(
            `orders/${orderId}/`
          );
        }

        let deletedObjects = 0;

        for (
          const prefix
            of prefixes
        ) {
          let cursor;

          do {
            const listed =
              await env.MY_BUCKET.list({
                prefix,
                limit: 1000,
                ...(cursor
                  ? { cursor }
                  : {})
              });

            const keys =
              (listed.objects || [])
                .map(
                  o => o.key
                )
                .filter(Boolean);

            if (keys.length) {
              await env.MY_BUCKET.delete(
                keys
              );

              deletedObjects +=
                keys.length;
            }

            cursor =
              listed.truncated
                ? listed.cursor
                : undefined;

          } while (cursor);
        }

        /* -------------------------------------------------
           FINAL RESPONSE
           ------------------------------------------------- */

        return json({
          ok: true,
          customer_id:
            customerId,
          customer_type:
            isAdditionalCustomer
              ? "additional"
              : "main",
          customer_ids_deleted:
            deleteCustomerIds,
          customers_deleted:
            deleteCustomerIds.length,
          order_ids:
            orderIds,
          orders_deleted:
            orderIds.length,
          d1_verified:
            true,
          r2_objects_deleted:
            deletedObjects,
          remaining
        });

      } catch (e) {
        return json({
          ok: false,
          error:
            String(
              e?.message || e
            )
        }, 500);
      }
    }


    /* =====================================================
       R2 TEST
       ===================================================== */

    if (
      url.pathname === "/api/r2-test"
    ) {
      await env.MY_BUCKET.put(
        "r2-test.txt",
        "R2 WORKS",
        {
          httpMetadata: {
            contentType:
              "text/plain"
          }
        }
      );

      return new Response(
        "R2 upload OK",
        {
          headers: corsHeaders
        }
      );
    }


    /* =====================================================
       R2 PUT
       ===================================================== */

    if (
      url.pathname.startsWith(
        "/api/r2/"
      ) &&
      request.method === "PUT"
    ) {
      const key =
        decodeURIComponent(
          url.pathname.slice(
            "/api/r2/".length
          )
        );

      if (!key) {
        return new Response(
          "Missing file name",
          {
            status: 400,
            headers: corsHeaders
          }
        );
      }

      await env.MY_BUCKET.put(
        key,
        request.body,
        {
          httpMetadata: {
            contentType:
              request.headers.get(
                "content-type"
              ) ||
              "application/octet-stream"
          }
        }
      );

      return json({
        ok: true,
        key
      });
    }


    /* =====================================================
       R2 GET
       ===================================================== */

    if (
      url.pathname.startsWith(
        "/api/r2/"
      ) &&
      request.method === "GET"
    ) {
      const key =
        decodeURIComponent(
          url.pathname.slice(
            "/api/r2/".length
          )
        );

      const object =
        await env.MY_BUCKET.get(
          key
        );

      if (!object) {
        return new Response(
          "File not found",
          {
            status: 404,
            headers: corsHeaders
          }
        );
      }

      const headers =
        new Headers(
          corsHeaders
        );

      headers.set(
        "Content-Type",
        object
          .httpMetadata
          ?.contentType ||
          "application/octet-stream"
      );

      headers.set(
        "Cache-Control",
        "no-store"
      );

      return new Response(
        object.body,
        {
          headers
        }
      );
    }


    /* =====================================================
       R2 DELETE
       ===================================================== */

    if (
      url.pathname.startsWith(
        "/api/r2/"
      ) &&
      request.method === "DELETE"
    ) {
      const key =
        decodeURIComponent(
          url.pathname.slice(
            "/api/r2/".length
          )
        );

      await env.MY_BUCKET.delete(
        key
      );

      return json({
        ok: true,
        key
      });
    }


    /* =====================================================
       STATIC ASSETS
       ===================================================== */

    if (env.ASSETS?.fetch) {
      return env.ASSETS.fetch(
        request
      );
    }

    return new Response(
      "Not found",
      {
        status: 404,
        headers: corsHeaders
      }
    );
  }
};/* =========================================================
   HELPERS
   ========================================================= */

function validCol(c) {
  return /^[A-Za-z_][A-Za-z0-9_]*$/.test(
    String(c || "")
  );
}


function safeCol(c) {
  if (!validCol(c)) {
    throw new Error(
      "Invalid column"
    );
  }

  return c;
}


/* =========================================================
   FILTER BUILDER
   ========================================================= */

function addFilters(
  filters,
  add
) {
  for (const f of filters) {
    if (!f || typeof f !== "object") {
      continue;
    }

    const field =
      safeCol(f.field);

    /* IN */

    if (Array.isArray(f.__in)) {
      if (!f.__in.length) {
        add(
          ` AND 0`,
          []
        );

        continue;
      }

      add(
        ` AND ${field} IN (` +
        `${f.__in
          .map(() => "?")
          .join(",")})`,
        f.__in
      );

      continue;
    }

    /* GREATER THAN */

    if (
      Object.prototype.hasOwnProperty.call(
        f,
        "__gt"
      )
    ) {
      add(
        ` AND ${field} > ?`,
        [f.__gt]
      );

      continue;
    }

    /* EQUAL */

    add(
      ` AND ${field} = ?`,
      [f.value]
    );
  }
}


/* =========================================================
   TABLE COLUMNS

   calculations is included.

   If calculations table does not exist yet,
   PRAGMA returns an empty column set and the
   cascade safely skips calculation deletion.
   ========================================================= */

async function tableColumns(
  db,
  table
) {
  const allowed =
    new Set([
      "customers",
      "orders",
      "measurements",
      "alterations",
      "payments",
      "designs",
      "calculations"
    ]);

  if (!allowed.has(table)) {
    throw new Error(
      "Invalid table"
    );
  }

  const r =
    await db
      .prepare(
        `PRAGMA table_info(${safeCol(table)})`
      )
      .all();

  return new Set(
    (r.results || [])
      .map(
        x =>
          String(
            x.name || ""
          )
      )
      .filter(Boolean)
  );
}


/* =========================================================
   INSERT
   ========================================================= */

async function insertRow(
  db,
  table,
  row
) {
  const cols =
    Object.keys(row)
      .filter(validCol);

  if (!cols.length) {
    throw new Error(
      "No valid columns"
    );
  }

  const sql =
    `INSERT INTO ${safeCol(table)} ` +
    `(${cols.join(",")}) ` +
    `VALUES (` +
    `${cols
      .map(() => "?")
      .join(",")}` +
    `)`;

  await db
    .prepare(sql)
    .bind(
      ...cols.map(
        c => row[c]
      )
    )
    .run();
}


/* =========================================================
   SELECT AFTER UPDATE
   ========================================================= */

async function selectAfter(
  db,
  table,
  owner,
  filters,
  single
) {
  let sql =
    `SELECT * FROM ${safeCol(table)} ` +
    `WHERE owner_id = ?`;

  const args = [
    owner
  ];

  addFilters(
    filters,
    (s, a) => {
      sql += s;
      args.push(...a);
    }
  );

  const r =
    await db
      .prepare(sql)
      .bind(...args)
      .all();

  const rows =
    r.results || [];

  if (
    single === "single"
  ) {
    if (
      rows.length !== 1
    ) {
      return json({
        data: null,
        error:
          rows.length
            ? "Multiple rows returned"
            : "No rows found"
      });
    }

    return json({
      data: rows[0],
      error: null
    });
  }

  return json({
    data:
      single === "maybe"
        ? (rows[0] || null)
        : rows,
    error: null
  });
}


/* =========================================================
   SUPABASE USER VERIFICATION
   ========================================================= */

async function verifySupabaseUser(
  token
) {
  try {
    const r =
      await fetch(
        `${SUPABASE_URL}/auth/v1/user`,
        {
          headers: {
            apikey:
              SUPABASE_ANON_KEY,
            Authorization:
              `Bearer ${token}`
          }
        }
      );

    if (!r.ok) {
      return null;
    }

    return await r.json();

  } catch {
    return null;
  }
}


/* =========================================================
   CORS
   ========================================================= */

function cors() {
  return {
    "Access-Control-Allow-Origin":
      "*",

    "Access-Control-Allow-Headers":
      "Authorization,Content-Type",

    "Access-Control-Allow-Methods":
      "GET,PUT,DELETE,POST,OPTIONS",

    "Content-Type":
      "application/json"
  };
}


/* =========================================================
   JSON RESPONSE
   ========================================================= */

function json(
  data,
  status = 200
) {
  return new Response(
    JSON.stringify(data),
    {
      status,
      headers: cors()
    }
  );
}
