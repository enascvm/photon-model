
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for HostDatastoreNameConflictConnectInfo complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="HostDatastoreNameConflictConnectInfo"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}HostDatastoreConnectInfo"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="newDatastoreName" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "HostDatastoreNameConflictConnectInfo", propOrder = {
    "newDatastoreName"
})
public class HostDatastoreNameConflictConnectInfo
    extends HostDatastoreConnectInfo
{

    @XmlElement(required = true)
    protected String newDatastoreName;

    /**
     * Gets the value of the newDatastoreName property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getNewDatastoreName() {
        return newDatastoreName;
    }

    /**
     * Sets the value of the newDatastoreName property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setNewDatastoreName(String value) {
        this.newDatastoreName = value;
    }

}
