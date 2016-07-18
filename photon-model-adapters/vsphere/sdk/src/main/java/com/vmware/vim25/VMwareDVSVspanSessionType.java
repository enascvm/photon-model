
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VMwareDVSVspanSessionType.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="VMwareDVSVspanSessionType"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="mixedDestMirror"/&gt;
 *     &lt;enumeration value="dvPortMirror"/&gt;
 *     &lt;enumeration value="remoteMirrorSource"/&gt;
 *     &lt;enumeration value="remoteMirrorDest"/&gt;
 *     &lt;enumeration value="encapsulatedRemoteMirrorSource"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "VMwareDVSVspanSessionType")
@XmlEnum
public enum VMwareDVSVspanSessionType {

    @XmlEnumValue("mixedDestMirror")
    MIXED_DEST_MIRROR("mixedDestMirror"),
    @XmlEnumValue("dvPortMirror")
    DV_PORT_MIRROR("dvPortMirror"),
    @XmlEnumValue("remoteMirrorSource")
    REMOTE_MIRROR_SOURCE("remoteMirrorSource"),
    @XmlEnumValue("remoteMirrorDest")
    REMOTE_MIRROR_DEST("remoteMirrorDest"),
    @XmlEnumValue("encapsulatedRemoteMirrorSource")
    ENCAPSULATED_REMOTE_MIRROR_SOURCE("encapsulatedRemoteMirrorSource");
    private final String value;

    VMwareDVSVspanSessionType(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static VMwareDVSVspanSessionType fromValue(String v) {
        for (VMwareDVSVspanSessionType c: VMwareDVSVspanSessionType.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
