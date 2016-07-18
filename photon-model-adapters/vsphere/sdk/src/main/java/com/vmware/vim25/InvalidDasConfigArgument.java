
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for InvalidDasConfigArgument complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="InvalidDasConfigArgument"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}InvalidArgument"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="entry" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/&gt;
 *         &lt;element name="clusterName" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "InvalidDasConfigArgument", propOrder = {
    "entry",
    "clusterName"
})
public class InvalidDasConfigArgument
    extends InvalidArgument
{

    protected String entry;
    protected String clusterName;

    /**
     * Gets the value of the entry property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getEntry() {
        return entry;
    }

    /**
     * Sets the value of the entry property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setEntry(String value) {
        this.entry = value;
    }

    /**
     * Gets the value of the clusterName property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getClusterName() {
        return clusterName;
    }

    /**
     * Sets the value of the clusterName property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setClusterName(String value) {
        this.clusterName = value;
    }

}
