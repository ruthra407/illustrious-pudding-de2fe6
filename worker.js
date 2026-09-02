export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    // R2: upload
    if (url.pathname.startsWith("/api/r2/") && request.method === "PUT") {
      const key = decodeURIComponent(url.pathname.slice(8));

      if (!key) {
        return new Response("Missing file name", { status: 400 });
      }

      await env.MY_BUCKET.put(key, request.body, {
        httpMetadata: {
          contentType:
            request.headers.get("content-type") || "application/octet-stream"
        }
      });

      return Response.json({
        ok: true,
        key
      });
    }

    // R2: read
    if (url.pathname.startsWith("/api/r2/") && request.method === "GET") {
      const key = decodeURIComponent(url.pathname.slice(8));
      const object = await env.MY_BUCKET.get(key);

      if (!object) {
        return new Response("File not found", { status: 404 });
      }

      return new Response(object.body, {
        headers: {
          "Content-Type":
            object.httpMetadata?.contentType || "application/octet-stream"
        }
      });
    }

    // R2: delete
    if (url.pathname.startsWith("/api/r2/") && request.method === "DELETE") {
      const key = decodeURIComponent(url.pathname.slice(8));
      await env.MY_BUCKET.delete(key);

      return Response.json({ ok: true, key });
    }

    // Normal app files
    return env.ASSETS.fetch(request);
  }
};
