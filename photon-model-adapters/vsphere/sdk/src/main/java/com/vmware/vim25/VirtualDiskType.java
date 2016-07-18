
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VirtualDiskType.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="VirtualDiskType"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="preallocated"/&gt;
 *     &lt;enumeration value="thin"/&gt;
 *     &lt;enumeration value="seSparse"/&gt;
 *     &lt;enumeration value="rdm"/&gt;
 *     &lt;enumeration value="rdmp"/&gt;
 *     &lt;enumeration value="raw"/&gt;
 *     &lt;enumeration value="delta"/&gt;
 *     &lt;enumeration value="sparse2Gb"/&gt;
 *     &lt;enumeration value="thick2Gb"/&gt;
 *     &lt;enumeration value="eagerZeroedThick"/&gt;
 *     &lt;enumeration value="sparseMonolithic"/&gt;
 *     &lt;enumeration value="flatMonolithic"/&gt;
 *     &lt;enumeration value="thick"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "VirtualDiskType")
@XmlEnum
public enum VirtualDiskType {

    @XmlEnumValue("preallocated")
    PREALLOCATED("preallocated"),
    @XmlEnumValue("thin")
    THIN("thin"),
    @XmlEnumValue("seSparse")
    SE_SPARSE("seSparse"),
    @XmlEnumValue("rdm")
    RDM("rdm"),
    @XmlEnumValue("rdmp")
    RDMP("rdmp"),
    @XmlEnumValue("raw")
    RAW("raw"),
    @XmlEnumValue("delta")
    DELTA("delta"),
    @XmlEnumValue("sparse2Gb")
    SPARSE_2_GB("sparse2Gb"),
    @XmlEnumValue("thick2Gb")
    THICK_2_GB("thick2Gb"),
    @XmlEnumValue("eagerZeroedThick")
    EAGER_ZEROED_THICK("eagerZeroedThick"),
    @XmlEnumValue("sparseMonolithic")
    SPARSE_MONOLITHIC("sparseMonolithic"),
    @XmlEnumValue("flatMonolithic")
    FLAT_MONOLITHIC("flatMonolithic"),
    @XmlEnumValue("thick")
    THICK("thick");
    private final String value;

    VirtualDiskType(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static VirtualDiskType fromValue(String v) {
        for (VirtualDiskType c: VirtualDiskType.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
