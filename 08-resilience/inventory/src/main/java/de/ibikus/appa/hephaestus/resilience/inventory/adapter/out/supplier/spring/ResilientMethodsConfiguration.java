package de.ibikus.appa.hephaestus.resilience.inventory.adapter.out.supplier.spring;

import org.springframework.context.annotation.Configuration;
import org.springframework.resilience.annotation.EnableResilientMethods;

/** Switches on @Retryable and @ConcurrencyLimit (Spring Framework 7, no extra dependency). */
@Configuration(proxyBeanMethods = false)
@EnableResilientMethods
class ResilientMethodsConfiguration {}
