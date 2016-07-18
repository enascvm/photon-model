
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for AuthenticationProfile complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="AuthenticationProfile"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}ApplyProfile"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="activeDirectory" type="{urn:vim25}ActiveDirectoryProfile" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "AuthenticationProfile", propOrder = {
    "activeDirectory"
})
public class AuthenticationProfile
    extends ApplyProfile
{

    protected ActiveDirectoryProfile activeDirectory;

    /**
     * Gets the value of the activeDirectory property.
     * 
     * @return
     *     possible object is
     *     {@link ActiveDirectoryProfile }
     *     
     */
    public ActiveDirectoryProfile getActiveDirectory() {
        return activeDirectory;
    }

    /**
     * Sets the value of the activeDirectory property.
     * 
     * @param value
     *     allowed object is
     *     {@link ActiveDirectoryProfile }
     *     
     */
    public void setActiveDirectory(ActiveDirectoryProfile value) {
        this.activeDirectory = value;
    }

}
