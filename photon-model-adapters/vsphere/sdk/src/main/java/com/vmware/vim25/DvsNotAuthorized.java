
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for DvsNotAuthorized complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="DvsNotAuthorized"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}DvsFault"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="sessionExtensionKey" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/&gt;
 *         &lt;element name="dvsExtensionKey" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "DvsNotAuthorized", propOrder = {
    "sessionExtensionKey",
    "dvsExtensionKey"
})
public class DvsNotAuthorized
    extends DvsFault
{

    protected String sessionExtensionKey;
    protected String dvsExtensionKey;

    /**
     * Gets the value of the sessionExtensionKey property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getSessionExtensionKey() {
        return sessionExtensionKey;
    }

    /**
     * Sets the value of the sessionExtensionKey property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setSessionExtensionKey(String value) {
        this.sessionExtensionKey = value;
    }

    /**
     * Gets the value of the dvsExtensionKey property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getDvsExtensionKey() {
        return dvsExtensionKey;
    }

    /**
     * Sets the value of the dvsExtensionKey property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setDvsExtensionKey(String value) {
        this.dvsExtensionKey = value;
    }

}
