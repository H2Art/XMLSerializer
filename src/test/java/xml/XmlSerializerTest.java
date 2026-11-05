package xml;

import demo.Address;
import demo.Person;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class XmlSerializerTest {

    // ---------- тестовые классы ----------

    public enum Color { RED, GREEN }

    public static class Base {
        @XmlField public String baseName;
        public Base() {}
    }

    public static class Node extends Base {
        @XmlField public String name;
        @XmlField(attribute = true) public int num;
        @XmlField(name = "renamed") public String custom;
        @XmlField public Node next;
        @XmlField public Node[] arr;
        public String ignored = "default";
        public Node() {}
        public Node(String name) { this.name = name; }
    }

    public static class Prims {
        @XmlField public int i;
        @XmlField public long l;
        @XmlField public double d;
        @XmlField public float f;
        @XmlField public boolean b;
        @XmlField public char c;
        @XmlField public short s;
        @XmlField public byte by;
        @XmlField public Color color;
        @XmlField public Integer boxed;
        @XmlField public String str;
        public Prims() {}
    }

    public static class Holder {
        @XmlField public Base poly;
        @XmlField public Object any;
        @XmlField public int[] ints;
        @XmlField public int[][] matrix;
        @XmlField public String[] strs;
        @XmlField public Node[] nodes;
        public Holder() {}
    }

    public static class NoFields {
        public int x = 1;
        public NoFields() {}
    }

    public static class NoCtor {
        @XmlField public int x;
        public NoCtor(int x) { this.x = x; }
    }

    public static class BadAttr {
        @XmlField(attribute = true) public Node n = new Node("x");
        public BadAttr() {}
    }

    public static class ReservedAttr {
        @XmlField(name = "id", attribute = true) public int v = 1;
        public ReservedAttr() {}
    }

    public static class Labeled {
        @XmlField(label = true) public String title;
        @XmlField public Labeled other;
        @XmlField public Labeled[] list;
        public Labeled() {}
        public Labeled(String t) { this.title = t; }
    }

    public static class BadLabel {
        @XmlField(label = true) public Node n = new Node("x");
        @XmlField public BadLabel self;
        public BadLabel() {}
    }

    // ---------- вспомогательное ----------

    @SuppressWarnings("unchecked")
    private static <T> T rt(T o) throws Exception {
        return (T) XmlSerializer.deserialize(XmlSerializer.serialize(o));
    }

    private static int count(String s, String sub) {
        int n = 0;
        for (int i = s.indexOf(sub); i >= 0; i = s.indexOf(sub, i + sub.length())) n++;
        return n;
    }

    private static final String NODE = "xml.XmlSerializerTest$Node";

    // ---------- позитивные: значения ----------

    @Test @DisplayName("Примитивы, обертки, String, enum, unicode")
    void primitivesRoundTrip() throws Exception {
        Prims p = new Prims();
        p.i = -7; p.l = Long.MAX_VALUE; p.d = 3.14159; p.f = 2.5f; p.b = true;
        p.c = 'я'; p.s = 300; p.by = -5; p.color = Color.GREEN; p.boxed = 42; p.str = "привет";
        Prims r = rt(p);
        assertEquals(-7, r.i);
        assertEquals(Long.MAX_VALUE, r.l);
        assertEquals(3.14159, r.d);
        assertEquals(2.5f, r.f);
        assertTrue(r.b);
        assertEquals('я', r.c);
        assertEquals(300, r.s);
        assertEquals(-5, r.by);
        assertEquals(Color.GREEN, r.color);
        assertEquals(42, r.boxed);
        assertEquals("привет", r.str);
    }

    @Test @DisplayName("null-поля пишутся как nil и читаются как null")
    void nullFields() throws Exception {
        Node n = new Node();
        String xml = XmlSerializer.serialize(n);
        assertTrue(xml.contains("nil=\"true\""));
        Node r = rt(n);
        assertNull(r.name);
        assertNull(r.next);
        assertNull(r.arr);
        assertNull(r.baseName);
    }

    @Test @DisplayName("Пустая строка и пробелы по краям сохраняются")
    void emptyAndWhitespaceStrings() throws Exception {
        Node n = new Node();
        n.name = "";
        n.custom = "  a  ";
        Node r = rt(n);
        assertEquals("", r.name);
        assertEquals("  a  ", r.custom);
    }

    @Test @DisplayName("Спецсимволы XML экранируются")
    void specialCharacters() throws Exception {
        String s = "<a & \"b\"> 'x' \n\t\r\n >";
        Node n = new Node(s);
        n.custom = s;
        Node r = rt(n);
        assertEquals(s, r.name);
        assertEquals(s, r.custom);
    }

    // ---------- позитивные: ссылки ----------

    @Test @DisplayName("Общий объект остается одним экземпляром")
    void sharedObject() throws Exception {
        Node shared = new Node("shared");
        Node root = new Node("root");
        root.next = shared;
        root.arr = new Node[]{shared, shared};
        Node r = rt(root);
        assertSame(r.next, r.arr[0]);
        assertSame(r.arr[0], r.arr[1]);
        assertEquals("shared", r.next.name);
    }

    @Test @DisplayName("Цикл a <-> b")
    void cycle() throws Exception {
        Node a = new Node("a");
        Node b = new Node("b");
        a.next = b;
        b.next = a;
        Node r = rt(a);
        assertEquals("b", r.next.name);
        assertSame(r, r.next.next);
    }

    @Test @DisplayName("Массив ссылается на самого владельца и содержит null")
    void selfReferenceInArray() throws Exception {
        Node a = new Node("a");
        a.arr = new Node[]{a, null};
        Node r = rt(a);
        assertSame(r, r.arr[0]);
        assertNull(r.arr[1]);
        assertEquals(2, r.arr.length);
    }

    @Test @DisplayName("В XML: один id на объект, повторы через idref")
    void xmlUsesIdref() throws Exception {
        Node shared = new Node("s");
        Node root = new Node("root");
        root.arr = new Node[]{shared, shared};
        String xml = XmlSerializer.serialize(root);
        assertEquals(3, count(xml, " id=\""));   // root, массив, shared
        assertEquals(1, count(xml, "idref=\"")); // второй элемент массива
    }

    // ---------- позитивные: структура классов ----------

    @Test @DisplayName("Поля родительского класса")
    void inheritance() throws Exception {
        Node n = new Node("n");
        n.baseName = "base";
        assertEquals("base", rt(n).baseName);
    }

    @Test @DisplayName("attribute=true и name=...")
    void attributeAndRename() throws Exception {
        Node n = new Node("n");
        n.num = 5;
        n.custom = "c";
        String xml = XmlSerializer.serialize(n);
        assertTrue(xml.contains("num=\"5\""));
        assertTrue(xml.contains("<renamed>c</renamed>"));
        Node r = rt(n);
        assertEquals(5, r.num);
        assertEquals("c", r.custom);
    }

    @Test @DisplayName("Поле без аннотации игнорируется")
    void ignoredField() throws Exception {
        Node n = new Node("n");
        n.ignored = "changed";
        String xml = XmlSerializer.serialize(n);
        assertFalse(xml.contains("ignored"));
        assertEquals("default", rt(n).ignored);
    }

    @Test @DisplayName("Массивы: int[], int[][] с null-строкой, String[] с null, Node[]")
    void arrays() throws Exception {
        Holder h = new Holder();
        h.ints = new int[]{1, 2, 3};
        h.matrix = new int[][]{{1, 2}, null, {3}};
        h.strs = new String[]{"a", null, "c"};
        Node n = new Node("n");
        h.nodes = new Node[]{n, n, null};
        Holder r = rt(h);
        assertArrayEquals(h.ints, r.ints);
        assertTrue(Arrays.deepEquals(h.matrix, r.matrix));
        assertArrayEquals(h.strs, r.strs);
        assertEquals(3, r.nodes.length);
        assertSame(r.nodes[0], r.nodes[1]);
        assertNull(r.nodes[2]);
    }

    @Test @DisplayName("Полиморфизм: в поле Base лежит Node")
    void polymorphicField() throws Exception {
        Holder h = new Holder();
        Node n = new Node("n");
        n.baseName = "bn";
        h.poly = n;
        Holder r = rt(h);
        Node rn = assertInstanceOf(Node.class, r.poly);
        assertEquals("n", rn.name);
        assertEquals("bn", rn.baseName);
    }

    @Test @DisplayName("Поле типа Object с простыми значениями")
    void objectFieldWithSimpleValue() throws Exception {
        Holder h = new Holder();
        h.any = 123;
        assertEquals(123, rt(h).any);
        h.any = "text";
        assertEquals("text", rt(h).any);
    }

    @Test @DisplayName("serialize -> deserialize -> serialize дает тот же XML")
    void idempotent() throws Exception {
        Node a = new Node("a");
        Node b = new Node("b");
        a.next = b;
        b.next = a;
        a.arr = new Node[]{a, b, null};
        String x1 = XmlSerializer.serialize(a);
        String x2 = XmlSerializer.serialize(XmlSerializer.deserialize(x1));
        assertEquals(x1, x2);
    }

    @Test @DisplayName("Демо-граф Person/Address")
    void demoGraph() throws Exception {
        Address addr = new Address("Riga", "Brivibas 1");
        Person a = new Person("Alice", 30, addr);
        Person b = new Person("Bob", 25, addr);
        a.friend = b;
        b.friend = a;
        a.colleagues = new Person[]{a, b, null};
        a.scores = new int[]{5, 4, 5};
        a.password = "changed";
        Person r = rt(a);
        assertEquals("Alice", r.name);
        assertEquals(30, r.age);
        assertSame(r.address, r.friend.address);
        assertSame(r, r.friend.friend);
        assertSame(r, r.colleagues[0]);
        assertSame(r.friend, r.colleagues[1]);
        assertNull(r.colleagues[2]);
        assertArrayEquals(new int[]{5, 4, 5}, r.scores);
        assertEquals("not-serialized", r.password);
    }

    // ---------- позитивные: API ----------

    @Test @DisplayName("deserialize(xml, Class)")
    void deserializeByClass() throws Exception {
        String xml = XmlSerializer.serialize(new Node("n"));
        Node r = XmlSerializer.deserialize(xml, Node.class);
        assertEquals("n", r.name);
        assertThrows(ClassCastException.class, () -> XmlSerializer.deserialize(xml, Address.class));
    }

    @Test @DisplayName("deserialize(xml, String className)")
    void deserializeByName() throws Exception {
        String xml = XmlSerializer.serialize(new Node("n"));
        Node r = XmlSerializer.deserialize(xml, Node.class.getName());
        assertEquals("n", r.name);
    }

    // ---------- негативные ----------

    @Test @DisplayName("serialize(null)")
    void serializeNull() {
        assertThrows(IllegalArgumentException.class, () -> XmlSerializer.serialize(null));
    }

    @Test @DisplayName("Класс без @XmlField")
    void noAnnotatedClass() {
        assertThrows(IllegalArgumentException.class, () -> XmlSerializer.serialize(new NoFields()));
    }

    @Test @DisplayName("attribute=true на сложном поле")
    void attributeOnComplexField() {
        assertThrows(IllegalArgumentException.class, () -> XmlSerializer.serialize(new BadAttr()));
    }

    @Test @DisplayName("Зарезервированное имя атрибута (id)")
    void reservedAttributeName() {
        assertThrows(IllegalArgumentException.class, () -> XmlSerializer.serialize(new ReservedAttr()));
    }

    @Test @DisplayName("Неизвестный idref")
    void unknownIdref() {
        String xml = "<root id=\"1\" type=\"" + NODE + "\"><next idref=\"99\"/></root>";
        assertThrows(IllegalArgumentException.class, () -> XmlSerializer.deserialize(xml));
    }

    @Test @DisplayName("Неизвестный класс в type")
    void unknownClass() {
        assertThrows(ClassNotFoundException.class,
                () -> XmlSerializer.deserialize("<root type=\"no.Such\"/>"));
    }

    @Test @DisplayName("Некорректное число")
    void badNumber() {
        String xml = "<root id=\"1\" type=\"" + NODE + "\" num=\"abc\"/>";
        assertThrows(NumberFormatException.class, () -> XmlSerializer.deserialize(xml));
    }

    @Test @DisplayName("Некорректный boolean")
    void badBoolean() {
        String xml = "<root id=\"1\" type=\"xml.XmlSerializerTest$Prims\"><b>yes</b></root>";
        assertThrows(IllegalArgumentException.class, () -> XmlSerializer.deserialize(xml));
    }

    @Test @DisplayName("Нет конструктора без аргументов")
    void noDefaultConstructor() throws Exception {
        String xml = XmlSerializer.serialize(new NoCtor(1));
        assertThrows(NoSuchMethodException.class, () -> XmlSerializer.deserialize(xml));
    }

    @Test @DisplayName("Битый XML")
    void brokenXml() {
        assertThrows(Exception.class, () -> XmlSerializer.deserialize("<root>"));
    }

    @Test @DisplayName("DOCTYPE запрещен")
    void doctypeForbidden() {
        String xml = "<!DOCTYPE foo [<!ENTITY x \"y\">]><root/>";
        assertThrows(Exception.class, () -> XmlSerializer.deserialize(xml));
    }

    @Test @DisplayName("У корня нет type")
    void missingTypeOnRoot() {
        assertThrows(IllegalArgumentException.class, () -> XmlSerializer.deserialize("<root/>"));
    }

    // ---------- label у idref ----------

    @Test @DisplayName("idref содержит label из поля с label=true (в т.ч. в массиве)")
    void labelOnIdref() throws Exception {
        Labeled a = new Labeled("A");
        Labeled b = new Labeled("B");
        a.other = b;
        b.other = a;
        a.list = new Labeled[]{b, a};
        String xml = XmlSerializer.serialize(a);
        assertTrue(xml.contains("idref=\"1\" label=\"A\""));
        assertTrue(xml.contains("idref=\"2\" label=\"B\""));
        Labeled r = rt(a);
        assertSame(r, r.other.other);
        assertSame(r.other, r.list[0]);
    }

    @Test @DisplayName("label экранируется")
    void labelEscaped() throws Exception {
        Labeled a = new Labeled("<x & \"y\">");
        a.other = a;
        String xml = XmlSerializer.serialize(a);
        assertTrue(xml.contains("label=\"&lt;x &amp; &quot;y&quot;&gt;\""));
        assertEquals("<x & \"y\">", rt(a).title);
    }

    @Test @DisplayName("Если label-поле null, атрибут не пишется")
    void labelNullOmitted() throws Exception {
        Labeled a = new Labeled();
        a.other = a;
        assertTrue(XmlSerializer.serialize(a).contains("<other idref=\"1\"/>"));
    }

    @Test @DisplayName("label=true на сложном поле дает ошибку")
    void labelOnComplexField() {
        BadLabel b = new BadLabel();
        b.self = b;
        assertThrows(IllegalArgumentException.class, () -> XmlSerializer.serialize(b));
    }

    @Test @DisplayName("При чтении label игнорируется")
    void labelIgnoredOnRead() throws Exception {
        String xml = "<root id=\"1\" type=\"xml.XmlSerializerTest$Labeled\">"
                + "<title>A</title><other idref=\"1\" label=\"WRONG\"/></root>";
        Labeled r = XmlSerializer.deserialize(xml, Labeled.class);
        assertSame(r, r.other);
    }
}