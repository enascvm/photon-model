
package com.vmware.vim25;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ArrayOfDatastoreVVolContainerFailoverPair complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="ArrayOfDatastoreVVolContainerFailoverPair"&gt;
 *   &lt;complexContent&gt;
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="DatastoreVVolContainerFailoverPair" type="{urn:vim25}DatastoreVVolContainerFailoverPair" maxOccurs="unbounded" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/restriction&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ArrayOfDatastoreVVolContainerFailoverPair", propOrder = {
    "datastoreVVolContainerFailoverPair"
})
public class ArrayOfDatastoreVVolContainerFailoverPair {

    @XmlElement(name = "DatastoreVVolContainerFailoverPair")
    protected List<DatastoreVVolContainerFailoverPair> datastoreVVolContainerFailoverPair;

    /**
     * Gets the value of the datastoreVVolContainerFailoverPair property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the datastoreVVolContainerFailoverPair property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getDatastoreVVolContainerFailoverPair().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link DatastoreVVolContainerFailoverPair }
     * 
     * 
     */
    public List<DatastoreVVolContainerFailoverPair> getDatastoreVVolContainerFailoverPair() {
        if (datastoreVVolContainerFailoverPair == null) {
            datastoreVVolContainerFailoverPair = new ArrayList<DatastoreVVolContainerFailoverPair>();
        }
        return this.datastoreVVolContainerFailoverPair;
    }

}
