
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VirtualMachineWindowsQuiesceSpecVssBackupContext.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="VirtualMachineWindowsQuiesceSpecVssBackupContext"&gt;
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string"&gt;
 *     &lt;enumeration value="ctx_auto"/&gt;
 *     &lt;enumeration value="ctx_backup"/&gt;
 *     &lt;enumeration value="ctx_file_share_backup"/&gt;
 *   &lt;/restriction&gt;
 * &lt;/simpleType&gt;
 * </pre>
 * 
 */
@XmlType(name = "VirtualMachineWindowsQuiesceSpecVssBackupContext")
@XmlEnum
public enum VirtualMachineWindowsQuiesceSpecVssBackupContext {

    @XmlEnumValue("ctx_auto")
    CTX_AUTO("ctx_auto"),
    @XmlEnumValue("ctx_backup")
    CTX_BACKUP("ctx_backup"),
    @XmlEnumValue("ctx_file_share_backup")
    CTX_FILE_SHARE_BACKUP("ctx_file_share_backup");
    private final String value;

    VirtualMachineWindowsQuiesceSpecVssBackupContext(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static VirtualMachineWindowsQuiesceSpecVssBackupContext fromValue(String v) {
        for (VirtualMachineWindowsQuiesceSpecVssBackupContext c: VirtualMachineWindowsQuiesceSpecVssBackupContext.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
