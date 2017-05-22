
package com.vmware.pbm;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for PbmLineOfServiceInfoLineOfServiceEnum.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="PbmLineOfServiceInfoLineOfServiceEnum"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="INSPECTION"/&gt;
 *     &lt;enumeration value="COMPRESSION"/&gt;
 *     &lt;enumeration value="ENCRYPTION"/&gt;
 *     &lt;enumeration value="REPLICATION"/&gt;
 *     &lt;enumeration value="CACHING"/&gt;
 *     &lt;enumeration value="PERSISTENCE"/&gt;
 *     &lt;enumeration value="DATA_PROVIDER"/&gt;
 *     &lt;enumeration value="DATASTORE_IO_CONTROL"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "PbmLineOfServiceInfoLineOfServiceEnum")
@XmlEnum
public enum PbmLineOfServiceInfoLineOfServiceEnum {

    INSPECTION,
    COMPRESSION,
    ENCRYPTION,
    REPLICATION,
    CACHING,
    PERSISTENCE,
    DATA_PROVIDER,
    DATASTORE_IO_CONTROL;

    public String value() {
        return name();
    }

    public static PbmLineOfServiceInfoLineOfServiceEnum fromValue(String v) {
        return valueOf(v);
    }

}
