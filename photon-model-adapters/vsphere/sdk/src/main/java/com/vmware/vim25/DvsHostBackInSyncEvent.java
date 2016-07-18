
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for DvsHostBackInSyncEvent complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="DvsHostBackInSyncEvent"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}DvsEvent"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="hostBackInSync" type="{urn:vim25}HostEventArgument"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "DvsHostBackInSyncEvent", propOrder = {
    "hostBackInSync"
})
public class DvsHostBackInSyncEvent
    extends DvsEvent
{

    @XmlElement(required = true)
    protected HostEventArgument hostBackInSync;

    /**
     * Gets the value of the hostBackInSync property.
     * 
     * @return
     *     possible object is
     *     {@link HostEventArgument }
     *     
     */
    public HostEventArgument getHostBackInSync() {
        return hostBackInSync;
    }

    /**
     * Sets the value of the hostBackInSync property.
     * 
     * @param value
     *     allowed object is
     *     {@link HostEventArgument }
     *     
     */
    public void setHostBackInSync(HostEventArgument value) {
        this.hostBackInSync = value;
    }

}
