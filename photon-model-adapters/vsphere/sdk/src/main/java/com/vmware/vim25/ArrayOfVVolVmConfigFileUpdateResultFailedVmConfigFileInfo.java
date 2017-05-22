
package com.vmware.vim25;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ArrayOfVVolVmConfigFileUpdateResultFailedVmConfigFileInfo complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="ArrayOfVVolVmConfigFileUpdateResultFailedVmConfigFileInfo"&gt;
 *   &lt;complexContent&gt;
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="VVolVmConfigFileUpdateResultFailedVmConfigFileInfo" type="{urn:vim25}VVolVmConfigFileUpdateResultFailedVmConfigFileInfo" maxOccurs="unbounded" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/restriction&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ArrayOfVVolVmConfigFileUpdateResultFailedVmConfigFileInfo", propOrder = {
    "vVolVmConfigFileUpdateResultFailedVmConfigFileInfo"
})
public class ArrayOfVVolVmConfigFileUpdateResultFailedVmConfigFileInfo {

    @XmlElement(name = "VVolVmConfigFileUpdateResultFailedVmConfigFileInfo")
    protected List<VVolVmConfigFileUpdateResultFailedVmConfigFileInfo> vVolVmConfigFileUpdateResultFailedVmConfigFileInfo;

    /**
     * Gets the value of the vVolVmConfigFileUpdateResultFailedVmConfigFileInfo property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the vVolVmConfigFileUpdateResultFailedVmConfigFileInfo property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getVVolVmConfigFileUpdateResultFailedVmConfigFileInfo().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link VVolVmConfigFileUpdateResultFailedVmConfigFileInfo }
     * 
     * 
     */
    public List<VVolVmConfigFileUpdateResultFailedVmConfigFileInfo> getVVolVmConfigFileUpdateResultFailedVmConfigFileInfo() {
        if (vVolVmConfigFileUpdateResultFailedVmConfigFileInfo == null) {
            vVolVmConfigFileUpdateResultFailedVmConfigFileInfo = new ArrayList<VVolVmConfigFileUpdateResultFailedVmConfigFileInfo>();
        }
        return this.vVolVmConfigFileUpdateResultFailedVmConfigFileInfo;
    }

}
