
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for HostNetStackInstanceSystemStackKey.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="HostNetStackInstanceSystemStackKey"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="defaultTcpipStack"/&gt;
 *     &lt;enumeration value="vmotion"/&gt;
 *     &lt;enumeration value="vSphereProvisioning"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "HostNetStackInstanceSystemStackKey")
@XmlEnum
public enum HostNetStackInstanceSystemStackKey {

    @XmlEnumValue("defaultTcpipStack")
    DEFAULT_TCPIP_STACK("defaultTcpipStack"),
    @XmlEnumValue("vmotion")
    VMOTION("vmotion"),
    @XmlEnumValue("vSphereProvisioning")
    V_SPHERE_PROVISIONING("vSphereProvisioning");
    private final String value;

    HostNetStackInstanceSystemStackKey(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static HostNetStackInstanceSystemStackKey fromValue(String v) {
        for (HostNetStackInstanceSystemStackKey c: HostNetStackInstanceSystemStackKey.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
