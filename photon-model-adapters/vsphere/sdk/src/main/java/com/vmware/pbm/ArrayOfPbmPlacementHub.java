
package com.vmware.pbm;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ArrayOfPbmPlacementHub complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="ArrayOfPbmPlacementHub"&gt;
 *   &lt;complexContent&gt;
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="PbmPlacementHub" type="{urn:pbm}PbmPlacementHub" maxOccurs="unbounded" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/restriction&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ArrayOfPbmPlacementHub", propOrder = {
    "pbmPlacementHub"
})
public class ArrayOfPbmPlacementHub {

    @XmlElement(name = "PbmPlacementHub")
    protected List<PbmPlacementHub> pbmPlacementHub;

    /**
     * Gets the value of the pbmPlacementHub property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the pbmPlacementHub property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getPbmPlacementHub().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link PbmPlacementHub }
     * 
     * 
     */
    public List<PbmPlacementHub> getPbmPlacementHub() {
        if (pbmPlacementHub == null) {
            pbmPlacementHub = new ArrayList<PbmPlacementHub>();
        }
        return this.pbmPlacementHub;
    }

}
