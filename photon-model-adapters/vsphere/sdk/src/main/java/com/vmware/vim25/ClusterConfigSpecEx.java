
package com.vmware.vim25;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for ClusterConfigSpecEx complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="ClusterConfigSpecEx"&gt;
 *   &lt;complexContent&gt;
 *     &lt;extension base="{urn:vim25}ComputeResourceConfigSpec"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="dasConfig" type="{urn:vim25}ClusterDasConfigInfo" minOccurs="0"/&gt;
 *         &lt;element name="dasVmConfigSpec" type="{urn:vim25}ClusterDasVmConfigSpec" maxOccurs="unbounded" minOccurs="0"/&gt;
 *         &lt;element name="drsConfig" type="{urn:vim25}ClusterDrsConfigInfo" minOccurs="0"/&gt;
 *         &lt;element name="drsVmConfigSpec" type="{urn:vim25}ClusterDrsVmConfigSpec" maxOccurs="unbounded" minOccurs="0"/&gt;
 *         &lt;element name="rulesSpec" type="{urn:vim25}ClusterRuleSpec" maxOccurs="unbounded" minOccurs="0"/&gt;
 *         &lt;element name="orchestration" type="{urn:vim25}ClusterOrchestrationInfo" minOccurs="0"/&gt;
 *         &lt;element name="vmOrchestrationSpec" type="{urn:vim25}ClusterVmOrchestrationSpec" maxOccurs="unbounded" minOccurs="0"/&gt;
 *         &lt;element name="dpmConfig" type="{urn:vim25}ClusterDpmConfigInfo" minOccurs="0"/&gt;
 *         &lt;element name="dpmHostConfigSpec" type="{urn:vim25}ClusterDpmHostConfigSpec" maxOccurs="unbounded" minOccurs="0"/&gt;
 *         &lt;element name="vsanConfig" type="{urn:vim25}VsanClusterConfigInfo" minOccurs="0"/&gt;
 *         &lt;element name="vsanHostConfigSpec" type="{urn:vim25}VsanHostConfigInfo" maxOccurs="unbounded" minOccurs="0"/&gt;
 *         &lt;element name="groupSpec" type="{urn:vim25}ClusterGroupSpec" maxOccurs="unbounded" minOccurs="0"/&gt;
 *         &lt;element name="infraUpdateHaConfig" type="{urn:vim25}ClusterInfraUpdateHaConfigInfo" minOccurs="0"/&gt;
 *         &lt;element name="proactiveDrsConfig" type="{urn:vim25}ClusterProactiveDrsConfigInfo" minOccurs="0"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/extension&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ClusterConfigSpecEx", propOrder = {
    "dasConfig",
    "dasVmConfigSpec",
    "drsConfig",
    "drsVmConfigSpec",
    "rulesSpec",
    "orchestration",
    "vmOrchestrationSpec",
    "dpmConfig",
    "dpmHostConfigSpec",
    "vsanConfig",
    "vsanHostConfigSpec",
    "groupSpec",
    "infraUpdateHaConfig",
    "proactiveDrsConfig"
})
public class ClusterConfigSpecEx
    extends ComputeResourceConfigSpec
{

    protected ClusterDasConfigInfo dasConfig;
    protected List<ClusterDasVmConfigSpec> dasVmConfigSpec;
    protected ClusterDrsConfigInfo drsConfig;
    protected List<ClusterDrsVmConfigSpec> drsVmConfigSpec;
    protected List<ClusterRuleSpec> rulesSpec;
    protected ClusterOrchestrationInfo orchestration;
    protected List<ClusterVmOrchestrationSpec> vmOrchestrationSpec;
    protected ClusterDpmConfigInfo dpmConfig;
    protected List<ClusterDpmHostConfigSpec> dpmHostConfigSpec;
    protected VsanClusterConfigInfo vsanConfig;
    protected List<VsanHostConfigInfo> vsanHostConfigSpec;
    protected List<ClusterGroupSpec> groupSpec;
    protected ClusterInfraUpdateHaConfigInfo infraUpdateHaConfig;
    protected ClusterProactiveDrsConfigInfo proactiveDrsConfig;

    /**
     * Gets the value of the dasConfig property.
     * 
     * @return
     *     possible object is
     *     {@link ClusterDasConfigInfo }
     *     
     */
    public ClusterDasConfigInfo getDasConfig() {
        return dasConfig;
    }

    /**
     * Sets the value of the dasConfig property.
     * 
     * @param value
     *     allowed object is
     *     {@link ClusterDasConfigInfo }
     *     
     */
    public void setDasConfig(ClusterDasConfigInfo value) {
        this.dasConfig = value;
    }

    /**
     * Gets the value of the dasVmConfigSpec property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the dasVmConfigSpec property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getDasVmConfigSpec().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link ClusterDasVmConfigSpec }
     * 
     * 
     */
    public List<ClusterDasVmConfigSpec> getDasVmConfigSpec() {
        if (dasVmConfigSpec == null) {
            dasVmConfigSpec = new ArrayList<ClusterDasVmConfigSpec>();
        }
        return this.dasVmConfigSpec;
    }

    /**
     * Gets the value of the drsConfig property.
     * 
     * @return
     *     possible object is
     *     {@link ClusterDrsConfigInfo }
     *     
     */
    public ClusterDrsConfigInfo getDrsConfig() {
        return drsConfig;
    }

    /**
     * Sets the value of the drsConfig property.
     * 
     * @param value
     *     allowed object is
     *     {@link ClusterDrsConfigInfo }
     *     
     */
    public void setDrsConfig(ClusterDrsConfigInfo value) {
        this.drsConfig = value;
    }

    /**
     * Gets the value of the drsVmConfigSpec property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the drsVmConfigSpec property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getDrsVmConfigSpec().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link ClusterDrsVmConfigSpec }
     * 
     * 
     */
    public List<ClusterDrsVmConfigSpec> getDrsVmConfigSpec() {
        if (drsVmConfigSpec == null) {
            drsVmConfigSpec = new ArrayList<ClusterDrsVmConfigSpec>();
        }
        return this.drsVmConfigSpec;
    }

    /**
     * Gets the value of the rulesSpec property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the rulesSpec property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getRulesSpec().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link ClusterRuleSpec }
     * 
     * 
     */
    public List<ClusterRuleSpec> getRulesSpec() {
        if (rulesSpec == null) {
            rulesSpec = new ArrayList<ClusterRuleSpec>();
        }
        return this.rulesSpec;
    }

    /**
     * Gets the value of the orchestration property.
     * 
     * @return
     *     possible object is
     *     {@link ClusterOrchestrationInfo }
     *     
     */
    public ClusterOrchestrationInfo getOrchestration() {
        return orchestration;
    }

    /**
     * Sets the value of the orchestration property.
     * 
     * @param value
     *     allowed object is
     *     {@link ClusterOrchestrationInfo }
     *     
     */
    public void setOrchestration(ClusterOrchestrationInfo value) {
        this.orchestration = value;
    }

    /**
     * Gets the value of the vmOrchestrationSpec property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the vmOrchestrationSpec property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getVmOrchestrationSpec().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link ClusterVmOrchestrationSpec }
     * 
     * 
     */
    public List<ClusterVmOrchestrationSpec> getVmOrchestrationSpec() {
        if (vmOrchestrationSpec == null) {
            vmOrchestrationSpec = new ArrayList<ClusterVmOrchestrationSpec>();
        }
        return this.vmOrchestrationSpec;
    }

    /**
     * Gets the value of the dpmConfig property.
     * 
     * @return
     *     possible object is
     *     {@link ClusterDpmConfigInfo }
     *     
     */
    public ClusterDpmConfigInfo getDpmConfig() {
        return dpmConfig;
    }

    /**
     * Sets the value of the dpmConfig property.
     * 
     * @param value
     *     allowed object is
     *     {@link ClusterDpmConfigInfo }
     *     
     */
    public void setDpmConfig(ClusterDpmConfigInfo value) {
        this.dpmConfig = value;
    }

    /**
     * Gets the value of the dpmHostConfigSpec property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the dpmHostConfigSpec property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getDpmHostConfigSpec().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link ClusterDpmHostConfigSpec }
     * 
     * 
     */
    public List<ClusterDpmHostConfigSpec> getDpmHostConfigSpec() {
        if (dpmHostConfigSpec == null) {
            dpmHostConfigSpec = new ArrayList<ClusterDpmHostConfigSpec>();
        }
        return this.dpmHostConfigSpec;
    }

    /**
     * Gets the value of the vsanConfig property.
     * 
     * @return
     *     possible object is
     *     {@link VsanClusterConfigInfo }
     *     
     */
    public VsanClusterConfigInfo getVsanConfig() {
        return vsanConfig;
    }

    /**
     * Sets the value of the vsanConfig property.
     * 
     * @param value
     *     allowed object is
     *     {@link VsanClusterConfigInfo }
     *     
     */
    public void setVsanConfig(VsanClusterConfigInfo value) {
        this.vsanConfig = value;
    }

    /**
     * Gets the value of the vsanHostConfigSpec property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the vsanHostConfigSpec property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getVsanHostConfigSpec().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link VsanHostConfigInfo }
     * 
     * 
     */
    public List<VsanHostConfigInfo> getVsanHostConfigSpec() {
        if (vsanHostConfigSpec == null) {
            vsanHostConfigSpec = new ArrayList<VsanHostConfigInfo>();
        }
        return this.vsanHostConfigSpec;
    }

    /**
     * Gets the value of the groupSpec property.
     * 
     * <p>
     * This accessor method returns a reference to the live list,
     * not a snapshot. Therefore any modification you make to the
     * returned list will be present inside the JAXB object.
     * This is why there is not a <CODE>set</CODE> method for the groupSpec property.
     * 
     * <p>
     * For example, to add a new item, do as follows:
     * <pre>
     *    getGroupSpec().add(newItem);
     * </pre>
     * 
     * 
     * <p>
     * Objects of the following type(s) are allowed in the list
     * {@link ClusterGroupSpec }
     * 
     * 
     */
    public List<ClusterGroupSpec> getGroupSpec() {
        if (groupSpec == null) {
            groupSpec = new ArrayList<ClusterGroupSpec>();
        }
        return this.groupSpec;
    }

    /**
     * Gets the value of the infraUpdateHaConfig property.
     * 
     * @return
     *     possible object is
     *     {@link ClusterInfraUpdateHaConfigInfo }
     *     
     */
    public ClusterInfraUpdateHaConfigInfo getInfraUpdateHaConfig() {
        return infraUpdateHaConfig;
    }

    /**
     * Sets the value of the infraUpdateHaConfig property.
     * 
     * @param value
     *     allowed object is
     *     {@link ClusterInfraUpdateHaConfigInfo }
     *     
     */
    public void setInfraUpdateHaConfig(ClusterInfraUpdateHaConfigInfo value) {
        this.infraUpdateHaConfig = value;
    }

    /**
     * Gets the value of the proactiveDrsConfig property.
     * 
     * @return
     *     possible object is
     *     {@link ClusterProactiveDrsConfigInfo }
     *     
     */
    public ClusterProactiveDrsConfigInfo getProactiveDrsConfig() {
        return proactiveDrsConfig;
    }

    /**
     * Sets the value of the proactiveDrsConfig property.
     * 
     * @param value
     *     allowed object is
     *     {@link ClusterProactiveDrsConfigInfo }
     *     
     */
    public void setProactiveDrsConfig(ClusterProactiveDrsConfigInfo value) {
        this.proactiveDrsConfig = value;
    }

}
