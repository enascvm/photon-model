
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for DatastoreEvent complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="DatastoreEvent"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}Event"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="datastore" type="{urn:vim25}DatastoreEventArgument" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "DatastoreEvent", propOrder = {
    "datastore"
})
@XmlSeeAlso({
    DatastoreDestroyedEvent.class,
    DatastoreRenamedEvent.class,
    DatastoreCapacityIncreasedEvent.class,
    DatastoreDuplicatedEvent.class,
    DatastoreFileEvent.class,
    NonVIWorkloadDetectedOnDatastoreEvent.class,
    DatastoreIORMReconfiguredEvent.class
})
public class DatastoreEvent
    extends Event
{

    protected DatastoreEventArgument datastore;

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

}
