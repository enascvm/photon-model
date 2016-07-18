
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for PatchInstallFailed complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="PatchInstallFailed"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}PlatformConfigFault"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="rolledBack" type="{http://www.w3.org/2001/XMLSchema}boolean"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "PatchInstallFailed", propOrder = {
    "rolledBack"
})
public class PatchInstallFailed
    extends PlatformConfigFault
{

    protected boolean rolledBack;

    /**
     * Gets the value of the rolledBack property.
     * 
     */
    public boolean isRolledBack() {
        return rolledBack;
    }

    /**
     * Sets the value of the rolledBack property.
     * 
     */
    public void setRolledBack(boolean value) {
        this.rolledBack = value;
    }

}
