
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for DatastoreFileMovedEvent complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="DatastoreFileMovedEvent"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}DatastoreFileEvent"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="sourceDatastore" type="{urn:vim25}DatastoreEventArgument"/&gt;
 *         &lt;element name="sourceFile" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "DatastoreFileMovedEvent", propOrder = {
    "sourceDatastore",
    "sourceFile"
})
public class DatastoreFileMovedEvent
    extends DatastoreFileEvent
{

    @XmlElement(required = true)
    protected DatastoreEventArgument sourceDatastore;
    @XmlElement(required = true)
    protected String sourceFile;

    /**
     * Gets the value of the sourceDatastore property.
     * 
     * @return
     *     possible object is
     *     {@link DatastoreEventArgument }
     *     
     */
    public DatastoreEventArgument getSourceDatastore() {
        return sourceDatastore;
    }

    /**
     * Sets the value of the sourceDatastore property.
     * 
     * @param value
     *     allowed object is
     *     {@link DatastoreEventArgument }
     *     
     */
    public void setSourceDatastore(DatastoreEventArgument value) {
        this.sourceDatastore = value;
    }

    /**
     * Gets the value of the sourceFile property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getSourceFile() {
        return sourceFile;
    }

    /**
     * Sets the value of the sourceFile property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setSourceFile(String value) {
        this.sourceFile = value;
    }

}
