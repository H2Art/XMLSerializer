package app;

import demo.Address;
import demo.Person;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.Stage;
import xml.XmlSerializer;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.IdentityHashMap;

public class Main extends Application {
    interface Action { void run() throws Exception; }

    private final TreeView<Prop> tree = new TreeView<>();
    private final TextArea xml = new TextArea();
    private final TextField className = new TextField("demo.Person");
    private final Label status = new Label("Готово");
    private Object current;

    public static void main(String[] args) { launch(args); }

    @Override
    public void start(Stage stage) {
        tree.setEditable(true);
        tree.setCellFactory(tv -> new PropCell());
        xml.setStyle("-fx-font-family: monospace;");

        Button bNew = btn("Создать по имени класса",
                () -> show(XmlSerializer.newInstance(Class.forName(className.getText().trim()))));
        Button bDemo = btn("Демо-граф", () -> show(demo()));
        Button bSer = btn("Сериализовать →", () -> {
            if (current == null) throw new IllegalStateException("Нет объекта");
            xml.setText(XmlSerializer.serialize(current));
        });
        Button bDe = btn("← Десериализовать", () -> show(XmlSerializer.deserialize(xml.getText())));

        HBox top = new HBox(8, new Label("Класс:"), className, bNew, bDemo);
        HBox.setHgrow(className, Priority.ALWAYS);
        HBox bottom = new HBox(8, bSer, bDe, status);
        top.setPadding(new Insets(8));
        bottom.setPadding(new Insets(8));

        BorderPane root = new BorderPane(new SplitPane(tree, xml));
        root.setTop(top);
        root.setBottom(bottom);
        stage.setScene(new Scene(root, 1000, 600));
        stage.setTitle("XML-сериализатор");
        stage.show();
        run(() -> show(demo()));
    }

    private Button btn(String text, Action a) {
        Button b = new Button(text);
        b.setOnAction(e -> run(a));
        return b;
    }

    private void run(Action a) {
        try {
            a.run();
            status.setText("OK");
        } catch (Exception ex) {
            Throwable t = ex;
            while (t.getCause() != null) t = t.getCause();
            status.setText("Ошибка: " + t);
        }
    }

    private static Object demo() {
        Address addr = new Address("Riga", "Brivibas 1");
        Person a = new Person("Alice", 30, addr);
        Person b = new Person("Bob", 25, addr);
        a.friend = b;
        b.friend = a; // цикл
        a.colleagues = new Person[]{a, b, null};
        a.scores = new int[]{5, 4, 5};
        return a;
    }

    // ---------- дерево объекта ----------

    private void show(Object o) {
        current = o;
        TreeItem<Prop> root = build(new Prop("root", null, null, -1, Object.class, o), new IdentityHashMap<>());
        tree.setRoot(root);
    }

    private TreeItem<Prop> build(Prop p, IdentityHashMap<Object, Integer> ids) {
        TreeItem<Prop> item = new TreeItem<>(p);
        item.setExpanded(true);
        Object v = p.value();
        if (v == null || XmlSerializer.isSimple(v.getClass())) return item;
        Integer seen = ids.get(v);
        if (seen != null) { p.ref = seen; return item; }
        p.id = ids.size() + 1;
        ids.put(v, p.id);
        if (v.getClass().isArray()) {
            Class<?> ct = v.getClass().getComponentType();
            for (int i = 0; i < Array.getLength(v); i++)
                item.getChildren().add(build(new Prop("[" + i + "]", v, null, i, ct, null), ids));
        } else {
            for (Field f : XmlSerializer.fields(v.getClass()))
                item.getChildren().add(build(new Prop(XmlSerializer.name(f), v, f, -1, f.getType(), null), ids));
        }
        return item;
    }

    /** Узел дерева: поле объекта, элемент массива или корень. */
    static final class Prop {
        final String label;
        final Object owner, root;
        final Field field;
        final int index;
        final Class<?> declared;
        int id, ref;

        Prop(String label, Object owner, Field field, int index, Class<?> declared, Object root) {
            this.label = label; this.owner = owner; this.field = field;
            this.index = index; this.declared = declared; this.root = root;
        }

        Object value() {
            try {
                if (field != null) return field.get(owner);
                if (index >= 0) return Array.get(owner, index);
                return root;
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }

        boolean editable() {
            if (owner == null) return false;
            Object v = value();
            return XmlSerializer.isSimple(v != null ? v.getClass() : declared);
        }

        String raw() { Object v = value(); return v == null ? "" : v.toString(); }

        void set(String text) throws Exception {
            Object v = value();
            Object nv = XmlSerializer.parseSimple(text, v != null ? v.getClass() : declared);
            if (field != null) field.set(owner, nv); else Array.set(owner, index, nv);
        }

        @Override
        public String toString() {
            if (ref > 0) return label + " → #" + ref;
            Object v = value();
            if (v == null) return label + " = null";
            Class<?> c = v.getClass();
            if (XmlSerializer.isSimple(c)) return label + " = " + v;
            String t = c.isArray() ? c.getComponentType().getSimpleName() + "[" + Array.getLength(v) + "]"
                    : c.getSimpleName();
            return label + " : " + t + " #" + id;
        }
    }

    /** Ячейка с редактированием простых значений (двойной клик). */
    static final class PropCell extends TreeCell<Prop> {
        private TextField tf;

        @Override
        protected void updateItem(Prop p, boolean empty) {
            super.updateItem(p, empty);
            if (empty || p == null) { setText(null); setGraphic(null); }
            else if (isEditing()) { setText(null); setGraphic(tf); }
            else { setText(p.toString()); setGraphic(null); }
        }

        @Override
        public void startEdit() {
            if (getItem() == null || !getItem().editable()) return;
            super.startEdit();
            tf = new TextField(getItem().raw());
            tf.setOnAction(e -> commit());
            tf.setOnKeyPressed(e -> { if (e.getCode() == KeyCode.ESCAPE) cancelEdit(); });
            setText(null);
            setGraphic(tf);
            tf.requestFocus();
            tf.selectAll();
        }

        @Override
        public void cancelEdit() {
            super.cancelEdit();
            setText(getItem() == null ? null : getItem().toString());
            setGraphic(null);
        }

        private void commit() {
            try {
                getItem().set(tf.getText());
            } catch (Exception ex) {
                Throwable t = ex;
                while (t.getCause() != null) t = t.getCause();
                new Alert(Alert.AlertType.ERROR, String.valueOf(t)).show();
                cancelEdit();
                return;
            }
            commitEdit(getItem());
            setText(getItem().toString());
            setGraphic(null);
        }
    }
}