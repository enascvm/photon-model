
package com.vmware.vim25;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for UsbScanCodeSpec complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="UsbScanCodeSpec"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}DynamicData"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="keyEvents" type="{urn:vim25}UsbScanCodeSpecKeyEvent" maxOccurs="unbounded"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "UsbScanCodeSpec", propOrder = {
    "keyEvents"
})
public class UsbScanCodeSpec
    extends DynamicData
{

    @XmlElement(required = true)
    protected List<UsbScanCodeSpecKeyEvent> keyEvents;

    /**
     * Gets the value of the keyEvents property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the keyEvents property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getKeyEvents().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link UsbScanCodeSpecKeyEvent }
     * 
     * 
     */
    public List<UsbScanCodeSpecKeyEvent> getKeyEvents() {
        if (keyEvents == null) {
            keyEvents = new ArrayList<UsbScanCodeSpecKeyEvent>();
        }
        return this.keyEvents;
    }

}
