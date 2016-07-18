
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for SSLVerifyFault complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="SSLVerifyFault"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}HostConnectFault"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="selfSigned" type="{http://www.w3.org/2001/XMLSchema}boolean"/&gt;
 *         &lt;element name="thumbprint" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "SSLVerifyFault", propOrder = {
    "selfSigned",
    "thumbprint"
})
public class SSLVerifyFault
    extends HostConnectFault
{

    protected boolean selfSigned;
    @XmlElement(required = true)
    protected String thumbprint;

    /**
     * Gets the value of the selfSigned property.
     * 
     */
    public boolean isSelfSigned() {
        return selfSigned;
    }

    /**
     * Sets the value of the selfSigned property.
     * 
     */
    public void setSelfSigned(boolean value) {
        this.selfSigned = value;
    }

    /**
     * Gets the value of the thumbprint property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getThumbprint() {
        return thumbprint;
    }

    /**
     * Sets the value of the thumbprint property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setThumbprint(String value) {
        this.thumbprint = value;
    }

}
