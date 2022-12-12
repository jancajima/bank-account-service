package com.nttdata.bankaccountservice.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Dto Customer.
 */
@Getter
@AllArgsConstructor
@Setter
@NoArgsConstructor
public class CustomerDto {

    public String id;
    //Full name of the customer
    private String name;
    //Email of the customer
    private String email;
    //Phone of the customer
    private String phone;
    //Document (DNI or RUC) of the customer
    private String document;
    //Customer's type: Person or Business
    private String type;
}
