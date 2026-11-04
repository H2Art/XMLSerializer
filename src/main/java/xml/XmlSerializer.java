package xml;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;

public final class XmlSerializer {
    private XmlSerializer() {}

    private static final Set<String> RESERVED = Set.of("id", "idref", "type", "nil");

    // ---------- публичный API ----------

    public static String serialize(Object root) throws Exception {
        if (root == null) throw new IllegalArgumentException("root == null");
        Writer w = new Writer();
        w.sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        w.write("root", root, Object.class, 0);
        return w.sb.toString();
    }

    /** Класс берется из атрибута type корневого элемента. */
    public static Object deserialize(String xml) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        f.setXIncludeAware(false);
        f.setExpandEntityReferences(false);
        Element root = f.newDocumentBuilder()
                .parse(new InputSource(new StringReader(xml))).getDocumentElement();
        return new Reader().read(root, Object.class);
    }

    public static <T> T deserialize(String xml, Class<T> cls) throws Exception {
        return cls.cast(deserialize(xml));
    }

    public static <T> T deserialize(String xml, String className) throws Exception {
        @SuppressWarnings("unchecked")
        Class<T> cls = (Class<T>) Class.forName(className);
        return deserialize(xml, cls);
    }

    // ---------- утилиты (используются и в GUI) ----------

    public static boolean isSimple(Class<?> c) {
        return c.isPrimitive() || c == String.class || c.isEnum()
                || c == Boolean.class || c == Character.class
                || (Number.class.isAssignableFrom(c) && c.getName().startsWith("java.lang."));
    }

    /** Поля с @XmlField по иерархии: сначала предки. */
    public static List<Field> fields(Class<?> c) {
        Deque<Class<?>> chain = new ArrayDeque<>();
        for (Class<?> k = c; k != null && k != Object.class; k = k.getSuperclass()) chain.addFirst(k);
        List<Field> res = new ArrayList<>();
        for (Class<?> k : chain)
            for (Field f : k.getDeclaredFields())
                if (f.isAnnotationPresent(XmlField.class) && !Modifier.isStatic(f.getModifiers())) {
                    f.setAccessible(true);
                    res.add(f);
                }
        return res;
    }

    public static String name(Field f) {
        String n = f.getAnnotation(XmlField.class).name();
        return n.isEmpty() ? f.getName() : n;
    }

    public static Object newInstance(Class<?> c) throws Exception {
        Constructor<?> k = c.getDeclaredConstructor();
        k.setAccessible(true);
        return k.newInstance();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Object parseSimple(String s, Class<?> t) {
        if (t == String.class) return s;
        if (t.isEnum()) return Enum.valueOf((Class) t, s);
        if (t == char.class || t == Character.class) {
            if (s.length() != 1) throw new IllegalArgumentException("Ожидается 1 символ: \"" + s + "\"");
            return s.charAt(0);
        }
        String x = s.trim();
        if (t == int.class || t == Integer.class) return Integer.valueOf(x);
        if (t == long.class || t == Long.class) return Long.valueOf(x);
        if (t == double.class || t == Double.class) return Double.valueOf(x);
        if (t == float.class || t == Float.class) return Float.valueOf(x);
        if (t == short.class || t == Short.class) return Short.valueOf(x);
        if (t == byte.class || t == Byte.class) return Byte.valueOf(x);
        if (t == boolean.class || t == Boolean.class) {
            if (!x.equals("true") && !x.equals("false"))
                throw new IllegalArgumentException("Ожидается true/false: " + s);
            return Boolean.valueOf(x);
        }
        throw new IllegalArgumentException("Неподдерживаемый тип: " + t.getName());
    }

    private static String esc(String s) {
        StringBuilder b = new StringBuilder();
        for (char ch : s.toCharArray())
            switch (ch) {
                case '&' -> b.append("&amp;");
                case '<' -> b.append("&lt;");
                case '>' -> b.append("&gt;");
                case '"' -> b.append("&quot;");
                case '\r' -> b.append("&#13;");
                case '\n' -> b.append("&#10;");
                case '\t' -> b.append("&#9;");
                default -> b.append(ch);
            }
        return b.toString();
    }

    private static List<Element> kids(Element e) {
        List<Element> r = new ArrayList<>();
        for (Node n = e.getFirstChild(); n != null; n = n.getNextSibling())
            if (n instanceof Element x) r.add(x);
        return r;
    }

    // ---------- запись ----------

    private static final class Writer {
        final StringBuilder sb = new StringBuilder();
        final IdentityHashMap<Object, Integer> ids = new IdentityHashMap<>();

        void write(String tag, Object v, Class<?> declared, int lvl) throws Exception {
            String pad = "  ".repeat(lvl);
            if (v == null) {
                sb.append(pad).append('<').append(tag).append(" nil=\"true\"/>\n");
                return;
            }
            Class<?> c = v.getClass();
            if (isSimple(c)) {
                sb.append(pad).append('<').append(tag);
                if (!declared.isPrimitive() && declared != c)
                    sb.append(" type=\"").append(c.getName()).append('"');
                sb.append('>').append(esc(v.toString())).append("</").append(tag).append(">\n");
                return;
            }
            Integer old = ids.get(v);
            if (old != null) {
                sb.append(pad).append('<').append(tag).append(" idref=\"").append(old).append("\"/>\n");
                return;
            }
            int id = ids.size() + 1;
            ids.put(v, id);
            sb.append(pad).append('<').append(tag)
                    .append(" id=\"").append(id).append("\" type=\"").append(c.getName()).append('"');
            if (c.isArray()) {
                sb.append(">\n");
                Class<?> ct = c.getComponentType();
                for (int i = 0; i < Array.getLength(v); i++)
                    write("item", Array.get(v, i), ct, lvl + 1);
            } else {
                List<Field> fs = fields(c);
                if (fs.isEmpty())
                    throw new IllegalArgumentException("Нет полей с @XmlField: " + c.getName());
                for (Field f : fs) {
                    if (!f.getAnnotation(XmlField.class).attribute()) continue;
                    if (!isSimple(f.getType()) || RESERVED.contains(name(f)))
                        throw new IllegalArgumentException("Недопустимый attribute=true: " + f);
                    Object fv = f.get(v);
                    if (fv != null)
                        sb.append(' ').append(name(f)).append("=\"").append(esc(fv.toString())).append('"');
                }
                sb.append(">\n");
                for (Field f : fs)
                    if (!f.getAnnotation(XmlField.class).attribute())
                        write(name(f), f.get(v), f.getType(), lvl + 1);
            }
            sb.append(pad).append("</").append(tag).append(">\n");
        }
    }

    // ---------- чтение ----------

    private static final class Reader {
        final Map<String, Object> objs = new HashMap<>();

        Object read(Element e, Class<?> declared) throws Exception {
            if ("true".equals(e.getAttribute("nil"))) return null;
            if (e.hasAttribute("idref")) {
                Object o = objs.get(e.getAttribute("idref"));
                if (o == null)
                    throw new IllegalArgumentException("Неизвестный idref=" + e.getAttribute("idref"));
                return o;
            }
            Class<?> c = e.hasAttribute("type")
                    ? Class.forName(e.getAttribute("type"), false, XmlSerializer.class.getClassLoader())
                    : declared;
            if (isSimple(c)) return parseSimple(e.getTextContent(), c);

            Object o;
            if (c.isArray()) {
                List<Element> items = kids(e);
                Class<?> ct = c.getComponentType();
                o = Array.newInstance(ct, items.size());
                reg(e, o);
                for (int i = 0; i < items.size(); i++)
                    Array.set(o, i, read(items.get(i), ct));
            } else {
                List<Field> fs = fields(c);
                if (fs.isEmpty())
                    throw new IllegalArgumentException("Нет полей с @XmlField (или не указан type): " + c.getName());
                o = newInstance(c);
                reg(e, o); // регистрируем ДО заполнения полей, иначе не работают циклы
                Map<String, Element> ch = new HashMap<>();
                for (Element k : kids(e)) ch.put(k.getTagName(), k);
                for (Field f : fs) {
                    String n = name(f);
                    if (f.getAnnotation(XmlField.class).attribute()) {
                        if (e.hasAttribute(n)) f.set(o, parseSimple(e.getAttribute(n), f.getType()));
                    } else if (ch.containsKey(n)) {
                        f.set(o, read(ch.get(n), f.getType()));
                    }
                }
            }
            return o;
        }

        void reg(Element e, Object o) {
            if (e.hasAttribute("id")) objs.put(e.getAttribute("id"), o);
        }
    }
}