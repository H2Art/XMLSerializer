package demo;

import xml.XmlField;

public class Address {
    @XmlField public String city;
    @XmlField(label = true) public String street;

    public Address() {}
    public Address(String city, String street) { this.city = city; this.street = street; }
}