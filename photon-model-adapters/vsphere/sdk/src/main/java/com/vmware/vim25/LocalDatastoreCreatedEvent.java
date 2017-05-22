
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for LocalDatastoreCreatedEvent complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="LocalDatastoreCreatedEvent"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}HostEvent"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="datastore" type="{urn:vim25}DatastoreEventArgument"/&gt;
 *         &lt;element name="datastoreUrl" type="{http://www.w3.org/2001/XMLSchema}string" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "LocalDatastoreCreatedEvent", propOrder = {
    "datastore",
    "datastoreUrl"
})
public class LocalDatastoreCreatedEvent
    extends HostEvent
{

    @XmlElement(required = true)
    protected DatastoreEventArgument datastore;
    protected String datastoreUrl;

    /**
     * Gets the value of the datastore property.
     * 
     * @return
     *     possible object is
     *     {@link DatastoreEventArgument }
     *     
     */
    public DatastoreEventArgument getDatastore() {
        return datastore;
    }

    /**
     * Sets the value of the datastore property.
     * 
     * @param value
     *     allowed object is
     *     {@link DatastoreEventArgument }
     *     
     */
    public void setDatastore(DatastoreEventArgument value) {
        this.datastore = value;
    }

    /**
     * Gets the value of the datastoreUrl property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getDatastoreUrl() {
        return datastoreUrl;
    }

    /**
     * Sets the value of the datastoreUrl property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setDatastoreUrl(String value) {
        this.datastoreUrl = value;
    }

}
