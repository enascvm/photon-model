
package com.vmware.vim25;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ClusterFixedSizeSlotPolicy complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="ClusterFixedSizeSlotPolicy"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}ClusterSlotPolicy"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="cpu" type="{http://www.w3.org/2001/XMLSchema}int"/&gt;
 *         &lt;element name="memory" type="{http://www.w3.org/2001/XMLSchema}int"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ClusterFixedSizeSlotPolicy", propOrder = {
    "cpu",
    "memory"
})
public class ClusterFixedSizeSlotPolicy
    extends ClusterSlotPolicy
{

    protected int cpu;
    protected int memory;

    /**
     * Gets the value of the cpu property.
     * 
     */
    public int getCpu() {
        return cpu;
    }

    /**
     * Sets the value of the cpu property.
     * 
     */
    public void setCpu(int value) {
        this.cpu = value;
    }

    /**
     * Gets the value of the memory property.
     * 
     */
    public int getMemory() {
        return memory;
    }

    /**
     * Sets the value of the memory property.
     * 
     */
    public void setMemory(int value) {
        this.memory = value;
    }

}
