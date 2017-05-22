
package com.vmware.pbm;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import com.vmware.vim25.DynamicData;


/**
 * <p>Java class for PbmDefaultProfileInfo complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="PbmDefaultProfileInfo"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}DynamicData"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="datastores" type="{urn:pbm}PbmPlacementHub" maxOccurs="unbounded"/&gt;
 *         &lt;element name="defaultProfile" type="{urn:pbm}PbmProfile" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "PbmDefaultProfileInfo", propOrder = {
    "datastores",
    "defaultProfile"
})
public class PbmDefaultProfileInfo
    extends DynamicData
{

    @XmlElement(required = true)
    protected List<PbmPlacementHub> datastores;
    protected PbmProfile defaultProfile;

    /**
     * Gets the value of the datastores property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the datastores property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getDatastores().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link PbmPlacementHub }
     * 
     * 
     */
    public List<PbmPlacementHub> getDatastores() {
        if (datastores == null) {
            datastores = new ArrayList<PbmPlacementHub>();
        }
        return this.datastores;
    }

    /**
     * Gets the value of the defaultProfile property.
     * 
     * @return
     *     possible object is
     *     {@link PbmProfile }
     *     
     */
    public PbmProfile getDefaultProfile() {
        return defaultProfile;
    }

    /**
     * Sets the value of the defaultProfile property.
     * 
     * @param value
     *     allowed object is
     *     {@link PbmProfile }
     *     
     */
    public void setDefaultProfile(PbmProfile value) {
        this.defaultProfile = value;
    }

}
