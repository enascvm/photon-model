
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for LockerReconfiguredEvent complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="LockerReconfiguredEvent"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}Event"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="oldDatastore" type="{urn:vim25}DatastoreEventArgument" minOccurs="0"/&gt;
 *         &lt;element name="newDatastore" type="{urn:vim25}DatastoreEventArgument" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "LockerReconfiguredEvent", propOrder = {
    "oldDatastore",
    "newDatastore"
})
public class LockerReconfiguredEvent
    extends Event
{

    protected DatastoreEventArgument oldDatastore;
    protected DatastoreEventArgument newDatastore;

    /**
     * Gets the value of the oldDatastore property.
     * 
     * @return
     *     possible object is
     *     {@link DatastoreEventArgument }
     *     
     */
    public DatastoreEventArgument getOldDatastore() {
        return oldDatastore;
    }

    /**
     * Sets the value of the oldDatastore property.
     * 
     * @param value
     *     allowed object is
     *     {@link DatastoreEventArgument }
     *     
     */
    public void setOldDatastore(DatastoreEventArgument value) {
        this.oldDatastore = value;
    }

    /**
     * Gets the value of the newDatastore property.
     * 
     * @return
     *     possible object is
     *     {@link DatastoreEventArgument }
     *     
     */
    public DatastoreEventArgument getNewDatastore() {
        return newDatastore;
    }

    /**
     * Sets the value of the newDatastore property.
     * 
     * @param value
     *     allowed object is
     *     {@link DatastoreEventArgument }
     *     
     */
    public void setNewDatastore(DatastoreEventArgument value) {
        this.newDatastore = value;
    }

}
