package core.log;

import core.framework.http.HTTPMethod;
import core.framework.json.Bean;
import core.framework.log.message.EventMessage;
import core.framework.log.message.LogTopics;
import core.framework.module.App;
import core.framework.module.SystemModule;
import core.framework.util.Strings;
import core.log.web.EventController;
import core.log.web.SendEventRequest;
import core.log.web.SendEventRequestValidator;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author neo
 */
public class LogCollectorApp extends App {
    @Override
    protected void initialize() {
        load(new SystemModule("sys.properties"));
        loadProperties("app.properties");
        http().maxForwardedIPs(3);      // loose x-forwarded-for ip config, there are cdn/proxy before system, and in event collector, preventing fake client ip is less important

        site().security();
        site().staticContent("/robots.txt");

        kafka().publish(LogTopics.TOPIC_EVENT, EventMessage.class);

        bind(SendEventRequestValidator.class);

        List<String> allowedOrigins = allowedOrigins(requiredProperty("app.allowedOrigins"));
        List<String> collectCookies = collectCookies(property("app.cookies").orElse(null));
        EventController controller = bind(new EventController(allowedOrigins, collectCookies));
        http().route(HTTPMethod.OPTIONS, "/event/:app", controller::options);
        http().route(HTTPMethod.POST, "/event/:app", controller::post);  // event will be sent via ajax or navigator.sendBeacon(), refer to https://developer.mozilla.org/en-US/docs/Web/API/Navigator/sendBeacon
        Bean.register(SendEventRequest.class);
    }

    List<String> collectCookies(String value) {
        if (value == null) return null;
        return parseList(value);
    }

    List<String> allowedOrigins(String value) {
        return parseList(value);
    }

    private List<String> parseList(String value) {
        String[] origins = Strings.split(value, ',');
        return Arrays.stream(origins).map(String::strip).collect(Collectors.toList());
    }
}
