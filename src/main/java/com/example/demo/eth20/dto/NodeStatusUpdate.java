package com.example.demo.eth20.dto;

import com.example.demo.eth20.entity.BlockchainNode;

public record NodeStatusUpdate(BlockchainNode.Status status) {}
