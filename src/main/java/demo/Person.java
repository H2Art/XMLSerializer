package demo;

import xml.XmlField;

public class Person {
    @XmlField public String name;
    @XmlField(attribute = true) public int age;
    @XmlField public Address address;
    @XmlField public Person friend;
    @XmlField(name = "team") public Person[] colleagues;
    @XmlField public int[] scores;
    public String password = "not-serialized"; // без аннотации: игнорируется

    public Person() {}
    public Person(String name, int age, Address address) {
        this.name = name; this.age = age; this.address = address;
    }
}