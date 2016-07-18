
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for EntityEventArgument complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="EntityEventArgument"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}EventArgument"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="name" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "EntityEventArgument", propOrder = {
    "name"
})
@XmlSeeAlso({
    ManagedEntityEventArgument.class,
    FolderEventArgument.class,
    DatacenterEventArgument.class,
    ComputeResourceEventArgument.class,
    ResourcePoolEventArgument.class,
    HostEventArgument.class,
    VmEventArgument.class,
    DatastoreEventArgument.class,
    NetworkEventArgument.class,
    AlarmEventArgument.class,
    ScheduledTaskEventArgument.class,
    DvsEventArgument.class
})
public class EntityEventArgument
    extends EventArgument
{

    @XmlElement(required = true)
    protected String name;

    /**
     * Gets the value of the name property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the value of the name property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setName(String value) {
        this.name = value;
    }

}
