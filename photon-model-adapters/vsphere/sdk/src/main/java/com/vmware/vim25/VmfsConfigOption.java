
package com.vmware.vim25;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for VmfsConfigOption complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="VmfsConfigOption"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}DynamicData"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="blockSizeOption" type="{http://www.w3.org/2001/XMLSchema}int"/&gt;
 *         &lt;element name="unmapGranularityOption" type="{http://www.w3.org/2001/XMLSchema}int" maxOccurs="unbounded" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "VmfsConfigOption", propOrder = {
    "blockSizeOption",
    "unmapGranularityOption"
})
public class VmfsConfigOption
    extends DynamicData
{

    protected int blockSizeOption;
    @XmlElement(type = Integer.class)
    protected List<Integer> unmapGranularityOption;

    /**
     * Gets the value of the blockSizeOption property.
     * 
     */
    public int getBlockSizeOption() {
        return blockSizeOption;
    }

    /**
     * Sets the value of the blockSizeOption property.
     * 
     */
    public void setBlockSizeOption(int value) {
        this.blockSizeOption = value;
    }

    /**
     * Gets the value of the unmapGranularityOption property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the unmapGranularityOption property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getUnmapGranularityOption().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link Integer }
     * 
     * 
     */
    public List<Integer> getUnmapGranularityOption() {
        if (unmapGranularityOption == null) {
            unmapGranularityOption = new ArrayList<Integer>();
        }
        return this.unmapGranularityOption;
    }

}
