
package com.vmware.vim25;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VVolVmConfigFileUpdateResult complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="VVolVmConfigFileUpdateResult"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}DynamicData"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="succeededVmConfigFile" type="{urn:vim25}KeyValue" maxOccurs="unbounded" minOccurs="0"/&gt;
 *         &lt;element name="failedVmConfigFile" type="{urn:vim25}VVolVmConfigFileUpdateResultFailedVmConfigFileInfo" maxOccurs="unbounded" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "VVolVmConfigFileUpdateResult", propOrder = {
    "succeededVmConfigFile",
    "failedVmConfigFile"
})
public class VVolVmConfigFileUpdateResult
    extends DynamicData
{

    protected List<KeyValue> succeededVmConfigFile;
    protected List<VVolVmConfigFileUpdateResultFailedVmConfigFileInfo> failedVmConfigFile;

    /**
     * Gets the value of the succeededVmConfigFile property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the succeededVmConfigFile property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getSucceededVmConfigFile().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link KeyValue }
     * 
     * 
     */
    public List<KeyValue> getSucceededVmConfigFile() {
        if (succeededVmConfigFile == null) {
            succeededVmConfigFile = new ArrayList<KeyValue>();
        }
        return this.succeededVmConfigFile;
    }

    /**
     * Gets the value of the failedVmConfigFile property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the failedVmConfigFile property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getFailedVmConfigFile().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link VVolVmConfigFileUpdateResultFailedVmConfigFileInfo }
     * 
     * 
     */
    public List<VVolVmConfigFileUpdateResultFailedVmConfigFileInfo> getFailedVmConfigFile() {
        if (failedVmConfigFile == null) {
            failedVmConfigFile = new ArrayList<VVolVmConfigFileUpdateResultFailedVmConfigFileInfo>();
        }
        return this.failedVmConfigFile;
    }

}
