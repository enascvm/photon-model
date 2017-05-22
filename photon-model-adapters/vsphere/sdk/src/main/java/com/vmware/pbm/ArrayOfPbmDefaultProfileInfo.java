
package com.vmware.pbm;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ArrayOfPbmDefaultProfileInfo complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="ArrayOfPbmDefaultProfileInfo"&gt;
 *   &lt;complexContent&gt;
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="PbmDefaultProfileInfo" type="{urn:pbm}PbmDefaultProfileInfo" maxOccurs="unbounded" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/restriction&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ArrayOfPbmDefaultProfileInfo", propOrder = {
    "pbmDefaultProfileInfo"
})
public class ArrayOfPbmDefaultProfileInfo {

    @XmlElement(name = "PbmDefaultProfileInfo")
    protected List<PbmDefaultProfileInfo> pbmDefaultProfileInfo;

    /**
     * Gets the value of the pbmDefaultProfileInfo property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the pbmDefaultProfileInfo property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getPbmDefaultProfileInfo().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link PbmDefaultProfileInfo }
     * 
     * 
     */
    public List<PbmDefaultProfileInfo> getPbmDefaultProfileInfo() {
        if (pbmDefaultProfileInfo == null) {
            pbmDefaultProfileInfo = new ArrayList<PbmDefaultProfileInfo>();
        }
        return this.pbmDefaultProfileInfo;
    }

}
