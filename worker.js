const SUPABASE_URL = "https://pdlsicwzfmartkwcwozx.supabase.co";
const SUPABASE_ANON_KEY = "sb_publishable_eCKne6Tzs6-QYSXpJb-aSQ_6AHe8n9O";

const TABLES = new Set([
  "customers",
  "orders",
  "measurements",
  "alterations",
  "payments",
  "designs"
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

    if (url.pathname === "/api/db" && request.method === "POST") {
      const auth = request.headers.get("Authorization") || "";
      const token = auth.startsWith("Bearer ")
        ? auth.slice(7)
        : "";

      if (!token) {
        return json({ error: "Authentication required" }, 401);
      }

      const user = await verifySupabaseUser(token);

      if (!user?.id) {
        return json({ error: "Invalid session" }, 401);
      }

      let body;

      try {
        body = await request.json();
      } catch {
        return json({ error: "Invalid JSON" }, 400);
      }

      const table = String(body.table || "");

      if (!TABLES.has(table)) {
        return json({ error: "Invalid table" }, 400);
      }

      try {
        const op = String(body.operation || "select");
        const filters = Array.isArray(body.filters)
          ? body.filters
          : [];

        if (op === "select") {
          let sql =
            `SELECT * FROM ${table} WHERE owner_id = ?`;

          const args = [user.id];

          addFilters(filters, (s, a) => {
            sql += s;
            args.push(...a);
          });

          if (body.order?.field) {
            sql +=
              ` ORDER BY ${safeCol(body.order.field)} ` +
              `${body.order.ascending === false ? "DESC" : "ASC"}`;
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
                ? rows[0] || null
                : rows,
            error: null
          });
        }

        if (op === "insert") {
          const input = Array.isArray(body.payload)
            ? body.payload
            : [body.payload];

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

          return json({
            data: body.single
              ? out[0] || null
              : out,
            error: null
          });
        }

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
            `UPDATE ${table} SET ` +
            `${cols
              .map(c => `${safeCol(c)} = ?`)
              .join(", ")}, updated_at = ? ` +
            `WHERE owner_id = ?`;

          const args = cols.map(c => payload[c]);

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

        if (op === "delete") {
          let sql =
            `DELETE FROM ${table} WHERE owner_id = ?`;

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

    /* CUSTOMER CASCADE DELETE */

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
        String(body.customer_id || "").trim();

      if (!customerId) {
        return json({
          error: "customer_id is required"
        }, 400);
      }

      try {
        const customer =
          await env.DB
            .prepare(
              `SELECT id
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

        const orderResult =
          await env.DB
            .prepare(
              `SELECT id
               FROM orders
               WHERE customer_id = ?
               AND owner_id = ?`
            )
            .bind(
              customerId,
              user.id
            )
            .all();

        const orderIds =
          (orderResult.results || [])
            .map(row => String(row.id || ""))
            .filter(Boolean);

        const statements = [];

        statements.push(
          env.DB.prepare(
            `DELETE FROM payments
             WHERE owner_id = ?
             AND customer_id = ?`
          ).bind(
            user.id,
            customerId
          )
        );

        for (const orderId of orderIds) {
          statements.push(
            env.DB.prepare(
              `DELETE FROM payments
               WHERE owner_id = ?
               AND order_id = ?`
            ).bind(
              user.id,
              orderId
            )
          );
        }

        statements.push(
          env.DB.prepare(
            `DELETE FROM alterations
             WHERE owner_id = ?
             AND customer_id = ?`
          ).bind(
            user.id,
            customerId
          )
        );

        for (const orderId of orderIds) {
          statements.push(
            env.DB.prepare(
              `DELETE FROM alterations
               WHERE owner_id = ?
               AND order_id = ?`
            ).bind(
              user.id,
              orderId
            )
          );
        }

        statements.push(
          env.DB.prepare(
            `DELETE FROM measurements
             WHERE owner_id = ?
             AND customer_id = ?`
          ).bind(
            user.id,
            customerId
          )
        );

        statements.push(
          env.DB.prepare(
            `DELETE FROM designs
             WHERE owner_id = ?
             AND customer_id = ?`
          ).bind(
            user.id,
            customerId
          )
        );

        statements.push(
          env.DB.prepare(
            `DELETE FROM orders
             WHERE owner_id = ?
             AND customer_id = ?`
          ).bind(
            user.id,
            customerId
          )
        );

        /* CUSTOMER DELETE */

        const customerDelete =
          await env.DB
            .prepare(
              `DELETE FROM customers
               WHERE id = ?
               RETURNING id`
            )
            .bind(customerId)
            .all();

        const deletedCustomerIds =
          customerDelete.results || [];

        if (deletedCustomerIds.length !== 1) {
          throw new Error(
            "Customer D1 delete failed"
          );
        }

        const customerStillThere =
          await env.DB
            .prepare(
              `SELECT id
               FROM customers
               WHERE id = ?
               LIMIT 1`
            )
            .bind(customerId)
            .first();

        if (customerStillThere) {
          throw new Error(
            "Customer D1 delete failed: customer row still exists"
          );
        }

        const prefixes = new Set([
          `${customerId}/`,
          `customers/${customerId}/`
        ]);

        for (const orderId of orderIds) {
          prefixes.add(
            `orders/${orderId}/`
          );
        }

        let deletedObjects = 0;

        for (const prefix of prefixes) {
          let cursor;

          do {
            const listOptions = {
              prefix,
              limit: 1000
            };

            if (cursor) {
              listOptions.cursor = cursor;
            }

            const listed =
              await env.MY_BUCKET.list(
                listOptions
              );

            const keys =
              (listed.objects || [])
                .map(obj => obj.key)
                .filter(Boolean);

            if (keys.length) {
              await env.MY_BUCKET.delete(keys);
              deletedObjects += keys.length;
            }

            cursor =
              listed.truncated
                ? listed.cursor
                : undefined;

          } while (cursor);
        }

        return json({
          ok: true,
          customer_id: customerId,
          order_ids: orderIds,
          orders_deleted: orderIds.length,
          r2_objects_deleted: deletedObjects
        });

      } catch (e) {
        console.error(
          "Customer cascade delete failed:",
          e
        );

        return json({
          ok: false,
          error: String(
            e?.message || e
          )
        }, 500);
      }
          }    /* =========================
       R2 TEST
    ========================= */

    if (
      url.pathname === "/api/r2-test"
    ) {
      await env.MY_BUCKET.put(
        "r2-test.txt",
        "R2 WORKS",
        {
          httpMetadata: {
            contentType: "text/plain"
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


    /* =========================
       R2 PUT / UPLOAD
    ========================= */

    if (
      url.pathname.startsWith("/api/r2/") &&
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


    /* =========================
       R2 GET / DOWNLOAD
    ========================= */

    if (
      url.pathname.startsWith("/api/r2/") &&
      request.method === "GET"
    ) {
      const key =
        decodeURIComponent(
          url.pathname.slice(
            "/api/r2/".length
          )
        );

      const object =
        await env.MY_BUCKET.get(key);

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
        new Headers(corsHeaders);

      headers.set(
        "Content-Type",
        object.httpMetadata?.contentType ||
        "application/octet-stream"
      );

      headers.set(
        "Cache-Control",
        "no-store"
      );

      return new Response(
        object.body,
        { headers }
      );
    }


    /* =========================
       R2 DELETE
    ========================= */

    if (
      url.pathname.startsWith("/api/r2/") &&
      request.method === "DELETE"
    ) {
      const key =
        decodeURIComponent(
          url.pathname.slice(
            "/api/r2/".length
          )
        );

      await env.MY_BUCKET.delete(key);

      return json({
        ok: true,
        key
      });
    }


    /* =========================
       STATIC ASSETS
    ========================= */

    if (env.ASSETS?.fetch) {
      return env.ASSETS.fetch(request);
    }

    return new Response(
      "Not found",
      {
        status: 404,
        headers: corsHeaders
      }
    );
  }
};


/* =====================================================
   HELPERS
===================================================== */

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


function addFilters(filters, add) {
  for (const f of filters) {

    const field =
      safeCol(f.field);

    if (Array.isArray(f.__in)) {

      if (!f.__in.length) {
        add(
          [` AND 0`],
          []
        );
        continue;
      }

      add(
        [
          ` AND ${field} IN (` +
          `${f.__in.map(() => "?").join(",")}` +
          `)`
        ],
        f.__in
      );

    } else if (
      Object.prototype.hasOwnProperty.call(
        f,
        "__gt"
      )
    ) {

      add(
        [` AND ${field} > ?`],
        [f.__gt]
      );

    } else {

      add(
        [` AND ${field} = ?`],
        [f.value]
      );
    }
  }
}


async function insertRow(
  db,
  table,
  row
) {
  const cols =
    Object.keys(row)
      .filter(validCol);

  const sql =
    `INSERT INTO ${table} ` +
    `(${cols.join(",")}) ` +
    `VALUES (` +
    `${cols.map(() => "?").join(",")}` +
    `)`;

  await db
    .prepare(sql)
    .bind(
      ...cols.map(c => row[c])
    )
    .run();
}


async function selectAfter(
  db,
  table,
  owner,
  filters,
  single
) {
  let sql =
    `SELECT * FROM ${table} ` +
    `WHERE owner_id = ?`;

  const args = [owner];

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

  if (single === "single") {

    if (rows.length !== 1) {
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
        ? rows[0] || null
        : rows,
    error: null
  });
}


async function verifySupabaseUser(
  token
) {
  const r =
    await fetch(
      `${SUPABASE_URL}/auth/v1/user`,
      {
        headers: {
          apikey: SUPABASE_ANON_KEY,
          Authorization:
            `Bearer ${token}`
        }
      }
    );

  if (!r.ok) {
    return null;
  }

  return r.json();
}


function cors() {
  return {
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Headers":
      "Authorization,Content-Type",
    "Access-Control-Allow-Methods":
      "GET,PUT,DELETE,POST,OPTIONS",
    "Content-Type":
      "application/json"
  };
}


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
