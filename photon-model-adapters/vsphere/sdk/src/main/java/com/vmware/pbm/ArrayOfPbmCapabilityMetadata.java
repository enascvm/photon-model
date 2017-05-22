
package com.vmware.pbm;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ArrayOfPbmCapabilityMetadata complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="ArrayOfPbmCapabilityMetadata"&gt;
 *   &lt;complexContent&gt;
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="PbmCapabilityMetadata" type="{urn:pbm}PbmCapabilityMetadata" maxOccurs="unbounded" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/restriction&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ArrayOfPbmCapabilityMetadata", propOrder = {
    "pbmCapabilityMetadata"
})
public class ArrayOfPbmCapabilityMetadata {

    @XmlElement(name = "PbmCapabilityMetadata")
    protected List<PbmCapabilityMetadata> pbmCapabilityMetadata;

    /**
     * Gets the value of the pbmCapabilityMetadata property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the pbmCapabilityMetadata property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getPbmCapabilityMetadata().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link PbmCapabilityMetadata }
     * 
     * 
     */
    public List<PbmCapabilityMetadata> getPbmCapabilityMetadata() {
        if (pbmCapabilityMetadata == null) {
            pbmCapabilityMetadata = new ArrayList<PbmCapabilityMetadata>();
        }
        return this.pbmCapabilityMetadata;
    }

}
