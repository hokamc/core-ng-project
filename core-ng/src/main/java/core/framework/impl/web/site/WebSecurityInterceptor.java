package core.framework.impl.web.site;

import core.framework.api.http.HTTPStatus;
import core.framework.http.ContentType;
import core.framework.web.Interceptor;
import core.framework.web.Invocation;
import core.framework.web.Request;
import core.framework.web.Response;

import java.util.Map;

import static java.net.URLEncoder.encode;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * @author neo
 */
public final class WebSecurityInterceptor implements Interceptor {    // refer to https://www.owasp.org/index.php/OWASP_Secure_Headers_Project#tab=Headers
    public String contentSecurityPolicy = "default-src 'self'; img-src 'self' data:; object-src 'none'; frame-src 'none';";

    @Override
    public Response intercept(Invocation invocation) throws Exception {
        Request request = invocation.context().request();
        if (!"https".equals(request.scheme())) {
            return Response.redirect(redirectURL(request), HTTPStatus.MOVED_PERMANENTLY);
        } else {
            Response response = invocation.proceed();
            appendSecurityHeaders(response);
            return response;
        }
    }

    void appendSecurityHeaders(Response response) {
        response.header("Strict-Transport-Security", "max-age=31536000");
        response.contentType().ifPresent(contentType -> {
            if (ContentType.TEXT_HTML.mediaType().equals(contentType.mediaType())) {
                response.header("Content-Security-Policy", contentSecurityPolicy);
                response.header("X-XSS-Protection", "1; mode=block");       // https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/X-XSS-Protection
            }
            response.header("X-Content-Type-Options", "nosniff");
        });
    }

    String redirectURL(Request request) {   // always assume https site is published on 443 port
        var builder = new StringBuilder("https://").append(request.hostName()).append(request.path());

        Map<String, String> queryParams = request.queryParams();
        if (!queryParams.isEmpty()) {
            int i = 0;
            for (Map.Entry<String, String> entry : queryParams.entrySet()) {
                String name = entry.getKey();
                String value = entry.getValue();
                if (i == 0) builder.append('?');
                else builder.append('&');
                builder.append(encode(name, UTF_8)).append('=').append(encode(value, UTF_8));
                i++;
            }
        }
        return builder.toString();
    }
}
