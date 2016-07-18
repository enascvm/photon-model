
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for OvfCreateImportSpecParamsDiskProvisioningType.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="OvfCreateImportSpecParamsDiskProvisioningType"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="monolithicSparse"/&gt;
 *     &lt;enumeration value="monolithicFlat"/&gt;
 *     &lt;enumeration value="twoGbMaxExtentSparse"/&gt;
 *     &lt;enumeration value="twoGbMaxExtentFlat"/&gt;
 *     &lt;enumeration value="thin"/&gt;
 *     &lt;enumeration value="thick"/&gt;
 *     &lt;enumeration value="seSparse"/&gt;
 *     &lt;enumeration value="eagerZeroedThick"/&gt;
 *     &lt;enumeration value="sparse"/&gt;
 *     &lt;enumeration value="flat"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "OvfCreateImportSpecParamsDiskProvisioningType")
@XmlEnum
public enum OvfCreateImportSpecParamsDiskProvisioningType {

    @XmlEnumValue("monolithicSparse")
    MONOLITHIC_SPARSE("monolithicSparse"),
    @XmlEnumValue("monolithicFlat")
    MONOLITHIC_FLAT("monolithicFlat"),
    @XmlEnumValue("twoGbMaxExtentSparse")
    TWO_GB_MAX_EXTENT_SPARSE("twoGbMaxExtentSparse"),
    @XmlEnumValue("twoGbMaxExtentFlat")
    TWO_GB_MAX_EXTENT_FLAT("twoGbMaxExtentFlat"),
    @XmlEnumValue("thin")
    THIN("thin"),
    @XmlEnumValue("thick")
    THICK("thick"),
    @XmlEnumValue("seSparse")
    SE_SPARSE("seSparse"),
    @XmlEnumValue("eagerZeroedThick")
    EAGER_ZEROED_THICK("eagerZeroedThick"),
    @XmlEnumValue("sparse")
    SPARSE("sparse"),
    @XmlEnumValue("flat")
    FLAT("flat");
    private final String value;

    OvfCreateImportSpecParamsDiskProvisioningType(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static OvfCreateImportSpecParamsDiskProvisioningType fromValue(String v) {
        for (OvfCreateImportSpecParamsDiskProvisioningType c: OvfCreateImportSpecParamsDiskProvisioningType.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
