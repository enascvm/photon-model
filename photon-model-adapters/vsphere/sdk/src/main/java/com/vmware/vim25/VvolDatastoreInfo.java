
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VvolDatastoreInfo complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="VvolDatastoreInfo"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}DatastoreInfo"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="vvolDS" type="{urn:vim25}HostVvolVolume" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "VvolDatastoreInfo", propOrder = {
    "vvolDS"
})
public class VvolDatastoreInfo
    extends DatastoreInfo
{

    protected HostVvolVolume vvolDS;

    /**
     * Gets the value of the vvolDS property.
     * 
     * @return
     *     possible object is
     *     {@link HostVvolVolume }
     *     
     */
    public HostVvolVolume getVvolDS() {
        return vvolDS;
    }

    /**
     * Sets the value of the vvolDS property.
     * 
     * @param value
     *     allowed object is
     *     {@link HostVvolVolume }
     *     
     */
    public void setVvolDS(HostVvolVolume value) {
        this.vvolDS = value;
    }

}
