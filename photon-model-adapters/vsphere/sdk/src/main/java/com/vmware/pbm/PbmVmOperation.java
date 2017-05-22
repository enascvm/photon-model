
package com.vmware.pbm;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for PbmVmOperation.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="PbmVmOperation"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="CREATE"/&gt;
 *     &lt;enumeration value="RECONFIGURE"/&gt;
 *     &lt;enumeration value="MIGRATE"/&gt;
 *     &lt;enumeration value="CLONE"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "PbmVmOperation")
@XmlEnum
public enum PbmVmOperation {

    CREATE,
    RECONFIGURE,
    MIGRATE,
    CLONE;

    public String value() {
        return name();
    }

    public static PbmVmOperation fromValue(String v) {
        return valueOf(v);
    }

}
