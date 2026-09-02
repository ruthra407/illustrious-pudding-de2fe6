export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    // CORS headers — Browser + Android APK/WebView
    const corsHeaders = {
      "Access-Control-Allow-Origin": "*",
      "Access-Control-Allow-Methods": "GET, PUT, DELETE, OPTIONS",
      "Access-Control-Allow-Headers": "Content-Type, Authorization",
      "Access-Control-Max-Age": "86400"
    };

    // CORS preflight
    if (
      request.method === "OPTIONS" &&
      url.pathname.startsWith("/api/r2/")
    ) {
      return new Response(null, {
        status: 204,
        headers: corsHeaders
      });
    }

    // R2 test upload
    if (url.pathname === "/api/r2-test") {
      await env.MY_BUCKET.put("r2-test.txt", "R2 WORKS", {
        httpMetadata: {
          contentType: "text/plain"
        }
      });

      return new Response("R2 upload OK", {
        headers: corsHeaders
      });
    }

    // R2: Upload
    if (
      url.pathname.startsWith("/api/r2/") &&
      request.method === "PUT"
    ) {
      const key = decodeURIComponent(url.pathname.slice(8));

      if (!key) {
        return new Response("Missing file name", {
          status: 400,
          headers: corsHeaders
        });
      }

      await env.MY_BUCKET.put(key, request.body, {
        httpMetadata: {
          contentType:
            request.headers.get("content-type") ||
            "application/octet-stream"
        }
      });

      return Response.json(
        {
          ok: true,
          key
        },
        {
          headers: corsHeaders
        }
      );
    }

    // R2: Read
    if (
      url.pathname.startsWith("/api/r2/") &&
      request.method === "GET"
    ) {
      const key = decodeURIComponent(url.pathname.slice(8));
      const object = await env.MY_BUCKET.get(key);

      if (!object) {
        return new Response("File not found", {
          status: 404,
          headers: corsHeaders
        });
      }

      const headers = new Headers(corsHeaders);

      headers.set(
        "Content-Type",
        object.httpMetadata?.contentType ||
          "application/octet-stream"
      );

      headers.set("Cache-Control", "no-store");

      return new Response(object.body, {
        headers
      });
    }

    // R2: Delete
    if (
      url.pathname.startsWith("/api/r2/") &&
      request.method === "DELETE"
    ) {
      const key = decodeURIComponent(url.pathname.slice(8));

      await env.MY_BUCKET.delete(key);

      return Response.json(
        {
          ok: true,
          key
        },
        {
          headers: corsHeaders
        }
      );
    }

    // Normal app files
    return env.ASSETS.fetch(request);
  }
};
