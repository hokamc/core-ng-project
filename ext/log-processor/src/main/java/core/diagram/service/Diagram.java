package core.diagram.service;

import core.framework.api.json.Property;

import java.util.ArrayList;
import java.util.List;

/**
 * @author allison
 */
public class Diagram {
    public String dot;
    public List<Note> notes = new ArrayList<>();

    public static class Note {
        @Property(name = "id")
        public String id;

        @Property(name = "html")
        public String html;
    }
}
