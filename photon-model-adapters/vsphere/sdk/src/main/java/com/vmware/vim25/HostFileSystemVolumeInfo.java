
package com.vmware.vim25;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for HostFileSystemVolumeInfo complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="HostFileSystemVolumeInfo"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}DynamicData"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="volumeTypeList" type="{http://www.w3.org/2001/XMLSchema}string" maxOccurs="unbounded" minOccurs="0"/&gt;
 *         &lt;element name="mountInfo" type="{urn:vim25}HostFileSystemMountInfo" maxOccurs="unbounded" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "HostFileSystemVolumeInfo", propOrder = {
    "volumeTypeList",
    "mountInfo"
})
public class HostFileSystemVolumeInfo
    extends DynamicData
{

    protected List<String> volumeTypeList;
    protected List<HostFileSystemMountInfo> mountInfo;

    /**
     * Gets the value of the volumeTypeList property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the volumeTypeList property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getVolumeTypeList().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link String }
     * 
     * 
     */
    public List<String> getVolumeTypeList() {
        if (volumeTypeList == null) {
            volumeTypeList = new ArrayList<String>();
        }
        return this.volumeTypeList;
    }

    /**
     * Gets the value of the mountInfo property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the mountInfo property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getMountInfo().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link HostFileSystemMountInfo }
     * 
     * 
     */
    public List<HostFileSystemMountInfo> getMountInfo() {
        if (mountInfo == null) {
            mountInfo = new ArrayList<HostFileSystemMountInfo>();
        }
        return this.mountInfo;
    }

}
