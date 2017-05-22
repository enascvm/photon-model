
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for IoFilterType.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="IoFilterType"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="cache"/&gt;
 *     &lt;enumeration value="replication"/&gt;
 *     &lt;enumeration value="encryption"/&gt;
 *     &lt;enumeration value="compression"/&gt;
 *     &lt;enumeration value="inspection"/&gt;
 *     &lt;enumeration value="datastoreIoControl"/&gt;
 *     &lt;enumeration value="dataProvider"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "IoFilterType")
@XmlEnum
public enum IoFilterType {

    @XmlEnumValue("cache")
    CACHE("cache"),
    @XmlEnumValue("replication")
    REPLICATION("replication"),
    @XmlEnumValue("encryption")
    ENCRYPTION("encryption"),
    @XmlEnumValue("compression")
    COMPRESSION("compression"),
    @XmlEnumValue("inspection")
    INSPECTION("inspection"),
    @XmlEnumValue("datastoreIoControl")
    DATASTORE_IO_CONTROL("datastoreIoControl"),
    @XmlEnumValue("dataProvider")
    DATA_PROVIDER("dataProvider");
    private final String value;

    IoFilterType(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static IoFilterType fromValue(String v) {
        for (IoFilterType c: IoFilterType.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
