/**
 * Copyright (c) 1997, 2015 by ProSyst Software GmbH and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.eclipse.smarthome.automation.integration.test;


import static org.junit.Assert.*

import static org.hamcrest.CoreMatchers.*
import static org.junit.Assert.*
import static org.junit.matchers.JUnitMatchers.*

import org.eclipse.smarthome.automation.Action
import org.eclipse.smarthome.automation.Condition
import org.eclipse.smarthome.automation.Rule
import org.eclipse.smarthome.automation.RuleProvider
import org.eclipse.smarthome.automation.RuleRegistry
import org.eclipse.smarthome.automation.RuleStatus
import org.eclipse.smarthome.automation.RuleStatusInfo
import org.eclipse.smarthome.automation.Trigger
import org.eclipse.smarthome.automation.Visibility
import org.eclipse.smarthome.automation.events.RuleAddedEvent
import org.eclipse.smarthome.automation.events.RuleRemovedEvent
import org.eclipse.smarthome.automation.events.RuleStatusInfoEvent
import org.eclipse.smarthome.automation.events.RuleUpdatedEvent
import org.eclipse.smarthome.automation.module.core.handler.GenericEventTriggerHandler
import org.eclipse.smarthome.automation.template.RuleTemplate
import org.eclipse.smarthome.automation.template.Template
import org.eclipse.smarthome.automation.template.TemplateProvider
import org.eclipse.smarthome.automation.template.TemplateRegistry
import org.eclipse.smarthome.automation.type.ActionType
import org.eclipse.smarthome.automation.type.ModuleTypeProvider
import org.eclipse.smarthome.automation.type.ModuleTypeRegistry
import org.eclipse.smarthome.automation.type.TriggerType
import org.eclipse.smarthome.config.core.ConfigDescriptionParameter
import org.eclipse.smarthome.config.core.ConfigDescriptionParameter.Type
import org.eclipse.smarthome.core.events.Event
import org.eclipse.smarthome.core.events.EventPublisher
import org.eclipse.smarthome.core.events.EventSubscriber
import org.eclipse.smarthome.core.items.ItemProvider
import org.eclipse.smarthome.core.items.ItemRegistry
import org.eclipse.smarthome.core.items.events.ItemEventFactory
import org.eclipse.smarthome.core.items.events.ItemStateEvent
import org.eclipse.smarthome.core.items.events.ItemUpdatedEvent
import org.eclipse.smarthome.core.library.items.SwitchItem
import org.eclipse.smarthome.core.library.types.OnOffType
import org.eclipse.smarthome.core.storage.StorageService
import org.eclipse.smarthome.core.types.Command
import org.eclipse.smarthome.core.types.TypeParser
import org.eclipse.smarthome.test.OSGiTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.osgi.framework.FrameworkUtil
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import com.google.common.collect.Sets

/**
 * this tests the RuleEngine
 * @author Benedikt Niehues - initial contribution
 * @author Marin Mitev - various fixes and extracted JSON parser test to separate file
 *
 */
class AutomationIntegrationTest extends OSGiTest{

    final Logger logger = LoggerFactory.getLogger(AutomationIntegrationTest.class)
    def EventPublisher eventPublisher
    def ItemRegistry itemRegistry
    def RuleRegistry ruleRegistry

    @Before
    void before() {
        logger.info('@Before.begin');

        getService(ItemRegistry)
        def itemProvider = [
            getAll: {
                [
                    new SwitchItem("myMotionItem"),
                    new SwitchItem("myPresenceItem"),
                    new SwitchItem("myLampItem"),
                    new SwitchItem("myMotionItem2"),
                    new SwitchItem("myPresenceItem2"),
                    new SwitchItem("myLampItem2"),
                    new SwitchItem("myMotionItem3"),
                    new SwitchItem("templ_MotionItem"),
                    new SwitchItem("templ_LampItem"),
                    new SwitchItem("myMotionItem3"),
                    new SwitchItem("myPresenceItem3"),
                    new SwitchItem("myLampItem3"),
                    new SwitchItem("myMotionItem4"),
                    new SwitchItem("myPresenceItem4"),
                    new SwitchItem("myLampItem4"),
                    new SwitchItem("myMotionItem5"),
                    new SwitchItem("myPresenceItem5"),
                    new SwitchItem("myLampItem5"),
                    new SwitchItem("xtempl_MotionItem"),
                    new SwitchItem("xtempl_LampItem")
                ]
            },
            addProviderChangeListener: {},
            removeProviderChangeListener: {},
            allItemsChanged: {}] as ItemProvider
        registerService(itemProvider)
        registerVolatileStorageService()

        enableItemAutoUpdate()

        def StorageService storageService = getService(StorageService)
        eventPublisher = getService(EventPublisher)
        itemRegistry = getService(ItemRegistry)
        ruleRegistry = getService(RuleRegistry)
        waitForAssert ({
            assertThat eventPublisher, is(notNullValue())
            assertThat storageService, is(notNullValue())
            assertThat itemRegistry, is(notNullValue())
            assertThat ruleRegistry, is(notNullValue())
        }, 9000)
        logger.info('@Before.finish');
    }

    @After
    void after() {
        logger.info('@After');
    }

    protected void registerVolatileStorageService() {
        registerService(AutomationIntegrationJsonTest.VOLATILE_STORAGE_SERVICE);
    }

    @Test
    public void 'assert that a rule can be added, updated and removed by the api' () {
        logger.info('assert that a rule can be added, updated and removed by the api');
        def ruleEvent = null

        def ruleEventHandler = [
            receive: {  Event e ->
                logger.info("RuleEvent: " + e.topic)
                ruleEvent = e
            },

            getSubscribedEventTypes: {
                Sets.newHashSet(RuleAddedEvent.TYPE, RuleRemovedEvent.TYPE, RuleUpdatedEvent.TYPE)
            },

            getEventFilter:{ null }
        ] as EventSubscriber
        registerService(ruleEventHandler)

        //ADD
        def Rule rule = createSimpleRule()
        ruleRegistry.add(rule)
        waitForAssert({
            assertThat ruleEvent, is(notNullValue())
            assertThat ruleEvent, is(instanceOf(RuleAddedEvent))
            def ruleAddedEvent = ruleEvent as RuleAddedEvent
            assertThat ruleAddedEvent.getRule().UID, is(rule.UID)
        })
        def Rule ruleAdded = ruleRegistry.get(rule.UID)
        assertThat ruleAdded, is(notNullValue())
        assertThat ruleRegistry.getStatus(rule.UID).getStatus(), is(RuleStatus.IDLE)


        //UPDATE
        ruleEvent = null
        ruleAdded.description="TestDescription"
        def Rule oldRule = ruleRegistry.update(ruleAdded)
        waitForAssert({
            assertThat ruleEvent, is(notNullValue())
            assertThat ruleEvent, is(instanceOf(RuleUpdatedEvent))
            def ruEvent = ruleEvent as RuleUpdatedEvent
            assertThat ruEvent.getRule().UID, is(rule.UID)
            assertThat ruEvent.getOldRule().UID, is(rule.UID)
            assertThat ruEvent.getRule().description, is("TestDescription")
            assertThat ruEvent.getOldRule().description, is(nullValue())
        })
        assertThat oldRule, is(notNullValue())
        assertThat oldRule, is(rule)

        //REMOVE
        ruleEvent = null
        def Rule removed = ruleRegistry.remove(rule.UID)
        waitForAssert({
            assertThat ruleEvent, is(notNullValue())
            assertThat ruleEvent, is(instanceOf(RuleRemovedEvent))
            def reEvent = ruleEvent as RuleRemovedEvent
            assertThat reEvent.getRule().UID, is(removed.UID)
        })
        assertThat removed, is(notNullValue())
        assertThat removed, is(ruleAdded)
        assertThat ruleRegistry.get(removed.UID), is(nullValue())
    }

    @Test
    public void 'assert that a rule with connections is executed' () {
        logger.info('assert that a rule with connections is executed');
        def triggerConfig = [eventSource:"myMotionItem3", eventTopic:"smarthome/*", eventTypes:"ItemStateEvent"]
        def condition1Config = [topic:"smarthome/*"]
        def actionConfig = [itemName:"myLampItem3", command:"ON"]
        def triggers = [
            new Trigger("ItemStateChangeTrigger", "GenericEventTrigger", triggerConfig)
        ]

        def inputs = [topic: "ItemStateChangeTrigger.topic"]

        //def conditionInputs=[topicConnection] as Set
        def conditions = [
            new Condition("EventCondition_2", "EventCondition", condition1Config, inputs)
        ]
        def actions = [
            new Action("ItemPostCommandAction2", "ItemPostCommandAction", actionConfig, null)
        ]

        def rule = new Rule("myRule21_ConnectionTest",triggers, conditions, actions, null, null)
        rule.name="RuleByJAVA_API"+new Random().nextInt()

        ruleRegistry.add(rule)

        logger.info("Rule created and added: "+rule.getUID())

        def ruleEvents = [] as List<RuleStatusInfoEvent>

        def ruleEventHandler = [
            receive: {  Event e ->
                logger.info("RuleEvent: " + e.topic)
                ruleEvents.add(e)
            },

            getSubscribedEventTypes: {
                Sets.newHashSet(RuleStatusInfoEvent.TYPE)
            },

            getEventFilter:{ null }
        ] as EventSubscriber
        registerService(ruleEventHandler)

        eventPublisher.post(ItemEventFactory.createStateEvent("myMotionItem3",OnOffType.ON))

        waitForAssert({
            assertThat ruleEvents.find{
                it.statusInfo.status == RuleStatus.RUNNING
            }, is(notNullValue())
        }, 9000, 200)
        waitForAssert({
            assertThat ruleRegistry.getStatus(rule.UID).getStatus(), is(not(RuleStatus.RUNNING))
        })
    }
    @Test
    public void 'assert that a rule with non existing moduleTypeHandler is added to the ruleRegistry in state NOT_INITIALIZED' () {
        logger.info('assert that a rule with non existing moduleTypeHandler is added to the ruleRegistry in state NOT_INITIALIZED');
        def triggerConfig = [eventSource:"myMotionItem", eventTopic:"smarthome/*", eventTypes:"ItemStateEvent"]
        def condition1Config = [topic:"smarthome/*"]
        def actionConfig = [itemName:"myLampItem3", command:"ON"]
        def triggers = [
            new Trigger("ItemStateChangeTrigger", "GenericEventTriggerWhichDoesNotExist", triggerConfig)
        ]
        def inputs = [topic: "ItemStateChangeTrigger.topic"]

        //def conditionInputs=[topicConnection] as Set
        def conditions = [
            new Condition("EventCondition_2", "EventCondition", condition1Config, inputs)
        ]
        def actions = [
            new Action("ItemPostCommandAction2", "ItemPostCommandAction", actionConfig, null)
        ]

        def rule = new Rule("myRule21_UNINITIALIZED",triggers, conditions, actions, null, null)
        rule.name="RuleByJAVA_API"+new Random().nextInt()

        ruleRegistry.add(rule)

        assertThat ruleRegistry.getStatus(rule.UID).getStatus(), is(RuleStatus.NOT_INITIALIZED)
    }

    @Test
    public void 'assert that a rule switches from IDLE to NOT_INITIALIZED if a moduleHanlder disappears and back to IDLE if it appears again' (){
        logger.info('assert that a rule switches from IDLE to NOT_INITIALIZED if a moduleHanlder disappears and back to IDLE if it appears again');
        def Rule rule = createSimpleRule()
        ruleRegistry.add(rule)
        assertThat ruleRegistry.getStatus(rule.UID).getStatus(), is(RuleStatus.IDLE)

        def moduleBundle = FrameworkUtil.getBundle(GenericEventTriggerHandler)
        moduleBundle.stop()
        waitForAssert({
            logger.info("RuleStatus: {}", ruleRegistry.getStatus(rule.UID).getStatus())
            assertThat ruleRegistry.getStatus(rule.UID).getStatus(), is(RuleStatus.NOT_INITIALIZED)
        },3000,100)


        moduleBundle.start()
        ruleRegistry.setEnabled(rule.UID,true)
        waitForAssert({
            logger.info("RuleStatus: {}", ruleRegistry.getStatus(rule.UID))
            assertThat ruleRegistry.getStatus(rule.UID).getStatus(), is(RuleStatus.IDLE)
        },3000,100)
    }


    @Test
    public void 'assert that a rule based on a composite modules is initialized and executed correctly' () {
        def triggerConfig = [itemName:"myMotionItem3"]
        def condition1Config = [itemName:"myPresenceItem3", state:"ON"]
        def eventInputs = [event:"ItemStateChangeTrigger3.event"]
        def condition2Config = [operator:"=", itemName:"myMotionItem3", state:"ON"]
        def actionConfig = [itemName:"myLampItem3", command:"ON"]
        def triggers = [
            new Trigger("ItemStateChangeTrigger3", "ItemStateChangeTrigger", triggerConfig)
        ]
        def conditions = [
            new Condition("ItemStateCondition5", "ItemStateEventCondition", condition1Config, eventInputs),
            new Condition("ItemStateCondition6", "ItemStateCondition", condition2Config, null)
        ]
        def actions = [
            new Action("ItemPostCommandAction3", "ItemPostCommandAction", actionConfig, null)
        ]

        def rule = new Rule("myRule21"+new Random().nextInt()+ "_COMPOSITE", triggers, conditions, actions, null, null)
        rule.name="RuleByJAVA_API_WIthCompositeTrigger"

        logger.info("Rule created: "+rule.getUID())

        def ruleRegistry = getService(RuleRegistry)
        ruleRegistry.add(rule)

        //TEST RULE
        waitForAssert({
            assertThat ruleRegistry.getStatus(rule.uid).getStatus(), is(RuleStatus.IDLE)
        })

        def EventPublisher eventPublisher = getService(EventPublisher)
        def ItemRegistry itemRegistry = getService(ItemRegistry)
        SwitchItem myMotionItem = itemRegistry.getItem("myMotionItem3")
        Command commandObj = TypeParser.parseCommand(myMotionItem.getAcceptedCommandTypes(), "ON")
        eventPublisher.post(ItemEventFactory.createCommandEvent("myPresenceItem3", commandObj))

        Event itemEvent = null

        def itemEventHandler = [
            receive: {  Event e ->
                logger.info("Event: " + e.topic)
                if (e.topic.contains("myLampItem3")){
                    itemEvent=e
                }
            },

            getSubscribedEventTypes: {
                Sets.newHashSet(ItemUpdatedEvent.TYPE, ItemStateEvent.TYPE)
            },

            getEventFilter:{ null }

        ] as EventSubscriber

        registerService(itemEventHandler)
        commandObj = TypeParser.parseCommand(itemRegistry.getItem("myMotionItem3").getAcceptedCommandTypes(),"ON")
        eventPublisher.post(ItemEventFactory.createCommandEvent("myMotionItem3", commandObj))
        waitForAssert ({ assertThat itemEvent, is(notNullValue())} , 3000, 100)
        assertThat itemEvent.topic, is(equalTo("smarthome/items/myLampItem3/state"))
        assertThat (((ItemStateEvent)itemEvent).itemState, is(OnOffType.ON))
        def myLampItem3 = itemRegistry.getItem("myLampItem3")
        assertThat myLampItem3, is(notNullValue())
        logger.info("myLampItem3 State: " + myLampItem3.state)
        assertThat myLampItem3.state, is(OnOffType.ON)
    }

    @Test
    public void 'test chain of composite Modules' () {
        def triggerConfig = [itemName:"myMotionItem4"]
        def condition1Config = [itemName:"myMotionItem4"]
        def eventInputs = [event:"ItemStateChangeTrigger4.event"]
        def condition2Config = [operator:"=", itemName:"myPresenceItem4", state:"ON"]
        def actionConfig = [itemName:"myLampItem4", command:"ON"]
        def triggers = [
            new Trigger("ItemStateChangeTrigger4", "ItemStateChangeTrigger", triggerConfig)
        ]
        def conditions = [
            new Condition("ItemStateCondition7", "ItemStateEvent_ON_Condition", condition1Config, eventInputs),
            new Condition("ItemStateCondition8", "ItemStateCondition", condition2Config, null)
        ]
        def actions = [
            new Action("ItemPostCommandAction4", "ItemPostCommandAction", actionConfig, null)
        ]

        def rule = new Rule("myRule21"+new Random().nextInt()+ "_COMPOSITE", triggers, conditions, actions, null, null)
        rule.name="RuleByJAVA_API_ChainedComposite"

        logger.info("Rule created: "+rule.getUID())

        def ruleRegistry = getService(RuleRegistry)
        ruleRegistry.add(rule)

        //TEST RULE
        waitForAssert({
            assertThat ruleRegistry.getStatus(rule.uid).getStatus(), is(RuleStatus.IDLE)
        })

        def EventPublisher eventPublisher = getService(EventPublisher)
        def ItemRegistry itemRegistry = getService(ItemRegistry)
        SwitchItem myMotionItem = itemRegistry.getItem("myMotionItem4")
        SwitchItem myPresenceItem = itemRegistry.getItem("myPresenceItem4")
        //prepare the presenceItems state to be on to match the second condition of the rule     
        myPresenceItem.send(OnOffType.ON)

        Event itemEvent = null

        def itemEventHandler = [
            receive: {  Event e ->
                logger.info("Event: " + e.topic)
                if (e.topic.contains("myLampItem4")){
                    itemEvent=e
                }
            },

            getSubscribedEventTypes: {
                Sets.newHashSet(ItemUpdatedEvent.TYPE, ItemStateEvent.TYPE)
            },

            getEventFilter:{ null }

        ] as EventSubscriber

        registerService(itemEventHandler)
        //causing the event to trigger the rule
        myMotionItem.send(OnOffType.ON)
        waitForAssert ({ assertThat itemEvent, is(notNullValue())} , 3000, 100)
        assertThat itemEvent.topic, is(equalTo("smarthome/items/myLampItem4/state"))
        assertThat (((ItemStateEvent)itemEvent).itemState, is(OnOffType.ON))
        def myLampItem4 = itemRegistry.getItem("myLampItem4")
        assertThat myLampItem4, is(notNullValue())
        logger.info("myLampItem4 State: " + myLampItem4.state)
        assertThat myLampItem4.state, is(OnOffType.ON)
    }

    @Test
    public void 'assert a rule added by api is executed as expected'() {
        logger.info('assert a rule added by api is executed as expected');
        //Creation of RULE
        def triggerConfig = [eventSource:"myMotionItem2", eventTopic:"smarthome/*", eventTypes:"ItemStateEvent"]
        def condition1Config = [operator:"=", itemName:"myPresenceItem2", state:"ON"]
        def condition2Config = [itemName:"myMotionItem2"]
        def actionConfig = [itemName:"myLampItem2", command:"ON"]
        def triggers = [
            new Trigger("ItemStateChangeTrigger2", "GenericEventTrigger", triggerConfig)
        ]
        def conditions = [
            new Condition("ItemStateCondition3", "ItemStateCondition", condition1Config, null),
            new Condition("ItemStateCondition4", "ItemStateEvent_ON_Condition", condition2Config, [event:"ItemStateChangeTrigger2.event"])
        ]
        def actions = [
            new Action("ItemPostCommandAction2", "ItemPostCommandAction", actionConfig, null)
        ]

        def rule = new Rule("myRule21",triggers, conditions, actions, null, null)
        rule.name="RuleByJAVA_API"
        def tags = ["myRule21"] as Set
        rule.tags = tags;

        logger.info("Rule created: "+rule.getUID())

        ruleRegistry.add(rule)
        ruleRegistry.setEnabled(rule.UID, true)

        //WAIT until Rule modules types are parsed and the rule becomes IDLE
        waitForAssert({
            assertThat ruleRegistry.getAll().isEmpty(), is(false)
            def rule2 = ruleRegistry.getAll().find{it.tags!=null && it.tags.contains("myRule21")} as Rule
            assertThat rule2, is(notNullValue())
            def ruleStatus2 = ruleRegistry.getStatus(rule2.uid) as RuleStatusInfo
            assertThat ruleStatus2.getStatus(), is(RuleStatus.IDLE)
        }, 10000, 200)


        //TEST RULE

        def EventPublisher eventPublisher = getService(EventPublisher)
        def ItemRegistry itemRegistry = getService(ItemRegistry)
        SwitchItem myMotionItem = itemRegistry.getItem("myMotionItem2")
        Command commandObj = TypeParser.parseCommand(myMotionItem.getAcceptedCommandTypes(), "ON")
        eventPublisher.post(ItemEventFactory.createCommandEvent("myPresenceItem2", commandObj))

        Event itemEvent = null

        def itemEventHandler = [
            receive: {  Event e ->
                logger.info("Event: " + e.topic)
                if (e.topic.contains("myLampItem2")){
                    itemEvent=e
                }
            },

            getSubscribedEventTypes: {
                Sets.newHashSet(ItemUpdatedEvent.TYPE, ItemStateEvent.TYPE)
            },

            getEventFilter:{ null }

        ] as EventSubscriber

        registerService(itemEventHandler)
        commandObj = TypeParser.parseCommand(itemRegistry.getItem("myMotionItem2").getAcceptedCommandTypes(),"ON")
        eventPublisher.post(ItemEventFactory.createCommandEvent("myMotionItem2", commandObj))
        waitForAssert ({ assertThat itemEvent, is(notNullValue())} , 3000, 100)
        assertThat itemEvent.topic, is(equalTo("smarthome/items/myLampItem2/state"))
        assertThat (((ItemStateEvent)itemEvent).itemState, is(OnOffType.ON))
        def myLampItem2 = itemRegistry.getItem("myLampItem2")
        assertThat myLampItem2, is(notNullValue())
        logger.info("myLampItem2 State: " + myLampItem2.state)
        assertThat myLampItem2.state, is(OnOffType.ON)
    }

    @Test
    public void 'assert that a rule can be added by a ruleProvider' () {
        logger.info('assert that a rule can be added by a ruleProvider');
        def rule = createSimpleRule()
        def ruleProvider = [
            getAll:{ [rule]},
            addProviderChangeListener:{},
            removeProviderChangeListener:{
            }
        ] as RuleProvider

        registerService(ruleProvider)
        assertThat ruleRegistry.getAll().find{it.UID==rule.UID}, is(notNullValue())
        unregisterService(ruleProvider)
        assertThat ruleRegistry.getAll().find{it.UID==rule.UID}, is(nullValue())
    }

    @Test
    public void 'assert that a rule created from a template is executed as expected' () {
        logger.info('assert that a rule created from a template is executed as expected');
        def templateRegistry = getService(TemplateRegistry)
        assertThat templateRegistry, is(notNullValue())
        def template = null
        waitForAssert({
            template = templateRegistry.get("SimpleTestTemplate") as Template
            assertThat template, is(notNullValue())
        })
        assertThat template.tags, is(notNullValue())
        assertThat template.tags.size(), is(not(0))
        def configs = [onItem:"templ_MotionItem", ifState: "ON", updateItem:"templ_LampItem", updateCommand:"ON"]
        def templateRule = new Rule("templateRuleUID", "SimpleTestTemplate", configs)
        ruleRegistry.add(templateRule)
        assertThat ruleRegistry.getAll().find{it.UID==templateRule.UID}, is(notNullValue())

        //bring the rule to execution:
        def commandObj = TypeParser.parseCommand(itemRegistry.getItem("templ_MotionItem").getAcceptedCommandTypes(),"ON")
        eventPublisher.post(ItemEventFactory.createCommandEvent("templ_MotionItem", commandObj))

        waitForAssert({
            def lamp = itemRegistry.getItem("templ_LampItem") as SwitchItem
            assertThat lamp.state, is(OnOffType.ON)
        })

    }

    @Test
    public void 'assert that a rule created from a more complex template is executed as expected' () {
        logger.info('assert that a rule created from a more complex template is executed as expected');
        def templateRegistry = getService(TemplateRegistry)
        assertThat templateRegistry, is(notNullValue())
        def template = null
        waitForAssert({
            template = templateRegistry.get("TestTemplateWithCompositeModules") as Template
            assertThat template, is(notNullValue())
        })
        assertThat template.tags, is(notNullValue())
        assertThat template.tags.size(), is(not(0))
        def configs = [onItem:"xtempl_MotionItem", ifState: ".*ON.*", updateItem:"xtempl_LampItem", updateCommand:"ON"]
        def templateRule = new Rule("xtemplateRuleUID", "TestTemplateWithCompositeModules", configs)
        ruleRegistry.add(templateRule)
        assertThat ruleRegistry.getAll().find{it.UID==templateRule.UID}, is(notNullValue())
        waitForAssert {
            assertThat ruleRegistry.get(templateRule.UID), is(notNullValue())
            assertThat ruleRegistry.getStatus(templateRule.UID).status, is(RuleStatus.IDLE)
        }

        //bring the rule to execution:
        def commandObj = TypeParser.parseCommand(itemRegistry.getItem("xtempl_MotionItem").getAcceptedCommandTypes(),"ON")
        eventPublisher.post(ItemEventFactory.createCommandEvent("xtempl_MotionItem", commandObj))

        waitForAssert({
            def lamp = itemRegistry.getItem("xtempl_LampItem") as SwitchItem
            assertThat lamp.state, is(OnOffType.ON)
        })

    }

    @Test
    public void 'test ModuleTypeProvider and TemplateProvider'(){
        logger.info('test ModuleTypeProvider and TemplateProvider');
        def templateRegistry = getService(TemplateRegistry)
        def moduleTypeRegistry = getService(ModuleTypeRegistry)
        def templateUID = 'testTemplate1'
        def tags = ["test", "testTag"] as Set
        def templateTriggers = []
        def templateConditions = []
        def templateActions = []
        def templateConfigDescriptionParameters = [
            new ConfigDescriptionParameter("param", Type.TEXT)
        ]

        def template = new RuleTemplate(templateUID, "Test template Label", "Test template description", tags, templateTriggers, templateConditions,
                templateActions, templateConfigDescriptionParameters, Visibility.VISIBLE)

        def triggerTypeUID = "testTrigger1"
        def triggerType = new TriggerType(triggerTypeUID, templateConfigDescriptionParameters, null)
        def actionTypeUID = "testAction1"
        def actionType = new ActionType(actionTypeUID, templateConfigDescriptionParameters, null)

        def templateProvider=[
            getTemplate:{ String UID, Locale locale ->
                if (UID == templateUID){
                    return template
                }else{
                    return null;
                }
            },

            getTemplates:{Locale locale->
                return [template]
            }
        ] as TemplateProvider

        def moduleTypeProvider=[
            getModuleType:{String UID, Locale locale->
                if (UID==triggerTypeUID){
                    return triggerType
                } else if (UID == actionTypeUID){
                    return actionType
                } else {
                    return null
                }
            },
            getModuleTypes:{Locale locale ->
                return [triggerType, actionType]
            }
        ] as ModuleTypeProvider

        registerService(templateProvider)
        assertThat templateRegistry.get(templateUID), is(notNullValue())
        registerService(moduleTypeProvider)
        assertThat moduleTypeRegistry.get(actionTypeUID), is(notNullValue())
        assertThat moduleTypeRegistry.get(triggerTypeUID), is(notNullValue())

        unregisterService(templateProvider)
        assertThat templateRegistry.get(templateUID), is(nullValue())
        unregisterService(moduleTypeProvider)
        assertThat moduleTypeRegistry.get(actionTypeUID), is(nullValue())
        assertThat moduleTypeRegistry.get(triggerTypeUID), is(nullValue())

    }

    /**
     * creates a simple rule
     */
    private Rule createSimpleRule(){
        logger.info("createSimpleRule")
        def rand = new Random().nextInt()
        def triggerConfig = [eventSource:"myMotionItem2", eventTopic:"smarthome/*", eventTypes:"ItemStateEvent"]
        def condition1Config = [operator:"=", itemName:"myPresenceItem2", state:"ON"]
        def condition2Config = [itemName:"myMotionItem2"]
        def actionConfig = [itemName:"myLampItem2", command:"ON"]
        def triggerUID = "ItemStateChangeTrigger_"+rand
        def triggers = [
            new Trigger(triggerUID, "GenericEventTrigger", triggerConfig)
        ]
        def conditions = [
            new Condition("ItemStateCondition_"+rand, "ItemStateCondition", condition1Config, null),
            new Condition("ItemStateCondition1_"+rand, "ItemStateEvent_ON_Condition", condition2Config, [event:triggerUID+".event"])
        ]
        def actions = [
            new Action("ItemPostCommandAction_"+rand, "ItemPostCommandAction", actionConfig, null)
        ]

        def rule = new Rule("myRule_"+rand,triggers, conditions, actions, null, null)
        rule.name="RuleByJAVA_API_"+rand

        logger.info("Rule created: "+rule.getUID())
        return rule
    }


    @Test
    public void 'assert a rule with generic condition works'() {
        def random = new Random().nextInt(100000)
        logger.info('assert a rule with generic condition works');
        //Creation of RULE
        def triggerConfig = [eventSource:"myMotionItem5", eventTopic:"smarthome/*", eventTypes:"ItemStateEvent"]
        def condition1Config = [operator:"matches", right:".*ON.*", inputproperty:"payload"]
        def condition2Config = [operator:"=", right:"myMotionItem5", inputproperty:"itemName"]
        def actionConfig = [itemName:"myLampItem5", command:"ON"]
        def triggerId = "ItemStateChangeTrigger"+random
        def triggers = [
            new Trigger(triggerId, "GenericEventTrigger", triggerConfig)
        ]
        def conditions = [
            new Condition("ItemStateCondition"+random, "GenericCompareCondition", condition1Config, [input:triggerId+".event"]),
            new Condition("ItemStateCondition"+(random+1), "GenericCompareCondition", condition2Config, [input:triggerId+".event"])
        ]
        def actions = [
            new Action("ItemPostCommandAction"+random, "ItemPostCommandAction", actionConfig, null)
        ]

        def rule = new Rule("myRule_"+random,triggers, conditions, actions, null, null)
        rule.name="RuleByJAVA_API"+random
        def tags = ["myRule_"+random] as Set
        rule.tags = tags;

        logger.info("Rule created: "+rule.getUID())

        ruleRegistry.add(rule)
        ruleRegistry.setEnabled(rule.UID, true)

        //WAIT until Rule modules types are parsed and the rule becomes IDLE
        waitForAssert({
            assertThat ruleRegistry.getAll().isEmpty(), is(false)
            def rule2 = ruleRegistry.get(rule.UID)
            assertThat rule2, is(notNullValue())
            def ruleStatus2 = ruleRegistry.getStatus(rule2.uid).status as RuleStatus
            assertThat ruleStatus2, is(RuleStatus.IDLE)
        }, 10000, 200)


        //TEST RULE

        def EventPublisher eventPublisher = getService(EventPublisher)
        def ItemRegistry itemRegistry = getService(ItemRegistry)
        SwitchItem myMotionItem = itemRegistry.getItem("myMotionItem5")
        Command commandObj = TypeParser.parseCommand(myMotionItem.getAcceptedCommandTypes(), "ON")
        eventPublisher.post(ItemEventFactory.createCommandEvent("myPresenceItem5", commandObj))

        Event itemEvent = null

        def itemEventHandler = [
            receive: {  Event e ->
                logger.info("Event: " + e.topic)
                if (e.topic.contains("myLampItem5")){
                    itemEvent=e
                }
            },

            getSubscribedEventTypes: {
                Sets.newHashSet(ItemUpdatedEvent.TYPE, ItemStateEvent.TYPE)
            },

            getEventFilter:{ null }

        ] as EventSubscriber

        registerService(itemEventHandler)
        commandObj = TypeParser.parseCommand(itemRegistry.getItem("myMotionItem5").getAcceptedCommandTypes(),"ON")
        eventPublisher.post(ItemEventFactory.createCommandEvent("myMotionItem5", commandObj))
        waitForAssert ({ assertThat itemEvent, is(notNullValue())} , 3000, 100)
        assertThat itemEvent.topic, is(equalTo("smarthome/items/myLampItem5/state"))
        assertThat (((ItemStateEvent)itemEvent).itemState, is(OnOffType.ON))
        def myLampItem2 = itemRegistry.getItem("myLampItem5")
        assertThat myLampItem2, is(notNullValue())
        logger.info("myLampItem5 State: " + myLampItem2.state)
        assertThat myLampItem2.state, is(OnOffType.ON)
    }

}

