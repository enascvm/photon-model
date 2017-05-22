
package com.vmware.pbm;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for PbmLegacyHubsNotSupported complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="PbmLegacyHubsNotSupported"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:pbm}PbmFault"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="hubs" type="{urn:pbm}PbmPlacementHub" maxOccurs="unbounded"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "PbmLegacyHubsNotSupported", propOrder = {
    "hubs"
})
public class PbmLegacyHubsNotSupported
    extends PbmFault
{

    @XmlElement(required = true)
    protected List<PbmPlacementHub> hubs;

    /**
     * Gets the value of the hubs property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the hubs property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getHubs().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link PbmPlacementHub }
     * 
     * 
     */
    public List<PbmPlacementHub> getHubs() {
        if (hubs == null) {
            hubs = new ArrayList<PbmPlacementHub>();
        }
        return this.hubs;
    }

}
