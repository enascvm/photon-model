
package com.vmware.pbm;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for PbmIofilterInfoFilterType.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="PbmIofilterInfoFilterType"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="INSPECTION"/&gt;
 *     &lt;enumeration value="COMPRESSION"/&gt;
 *     &lt;enumeration value="ENCRYPTION"/&gt;
 *     &lt;enumeration value="REPLICATION"/&gt;
 *     &lt;enumeration value="CACHE"/&gt;
 *     &lt;enumeration value="DATAPROVIDER"/&gt;
 *     &lt;enumeration value="DATASTOREIOCONTROL"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "PbmIofilterInfoFilterType")
@XmlEnum
public enum PbmIofilterInfoFilterType {

    INSPECTION,
    COMPRESSION,
    ENCRYPTION,
    REPLICATION,
    CACHE,
    DATAPROVIDER,
    DATASTOREIOCONTROL;

    public String value() {
        return name();
    }

    public static PbmIofilterInfoFilterType fromValue(String v) {
        return valueOf(v);
    }

}
