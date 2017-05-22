
package com.vmware.pbm;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import com.vmware.vim25.DynamicData;


/**
 * <p>Java class for PbmCapabilityMetadataPerCategory complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="PbmCapabilityMetadataPerCategory"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}DynamicData"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="subCategory" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *         &lt;element name="capabilityMetadata" type="{urn:pbm}PbmCapabilityMetadata" maxOccurs="unbounded"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "PbmCapabilityMetadataPerCategory", propOrder = {
    "subCategory",
    "capabilityMetadata"
})
public class PbmCapabilityMetadataPerCategory
    extends DynamicData
{

    @XmlElement(required = true)
    protected String subCategory;
    @XmlElement(required = true)
    protected List<PbmCapabilityMetadata> capabilityMetadata;

    /**
     * Gets the value of the subCategory property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getSubCategory() {
        return subCategory;
    }

    /**
     * Sets the value of the subCategory property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setSubCategory(String value) {
        this.subCategory = value;
    }

    /**
     * Gets the value of the capabilityMetadata property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the capabilityMetadata property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getCapabilityMetadata().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link PbmCapabilityMetadata }
     * 
     * 
     */
    public List<PbmCapabilityMetadata> getCapabilityMetadata() {
        if (capabilityMetadata == null) {
            capabilityMetadata = new ArrayList<PbmCapabilityMetadata>();
        }
        return this.capabilityMetadata;
    }

}
