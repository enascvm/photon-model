
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for DatastorePrincipalConfigured complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="DatastorePrincipalConfigured"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}HostEvent"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="datastorePrincipal" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "DatastorePrincipalConfigured", propOrder = {
    "datastorePrincipal"
})
public class DatastorePrincipalConfigured
    extends HostEvent
{

    @XmlElement(required = true)
    protected String datastorePrincipal;

    /**
     * Gets the value of the datastorePrincipal property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getDatastorePrincipal() {
        return datastorePrincipal;
    }

    /**
     * Sets the value of the datastorePrincipal property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setDatastorePrincipal(String value) {
        this.datastorePrincipal = value;
    }

}
