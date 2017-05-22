
package com.vmware.pbm;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ArrayOfPbmPlacementMatchingResources complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="ArrayOfPbmPlacementMatchingResources"&gt;
 *   &lt;complexContent&gt;
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="PbmPlacementMatchingResources" type="{urn:pbm}PbmPlacementMatchingResources" maxOccurs="unbounded" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/restriction&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ArrayOfPbmPlacementMatchingResources", propOrder = {
    "pbmPlacementMatchingResources"
})
public class ArrayOfPbmPlacementMatchingResources {

    @XmlElement(name = "PbmPlacementMatchingResources")
    protected List<PbmPlacementMatchingResources> pbmPlacementMatchingResources;

    /**
     * Gets the value of the pbmPlacementMatchingResources property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the pbmPlacementMatchingResources property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getPbmPlacementMatchingResources().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link PbmPlacementMatchingResources }
     * 
     * 
     */
    public List<PbmPlacementMatchingResources> getPbmPlacementMatchingResources() {
        if (pbmPlacementMatchingResources == null) {
            pbmPlacementMatchingResources = new ArrayList<PbmPlacementMatchingResources>();
        }
        return this.pbmPlacementMatchingResources;
    }

}
