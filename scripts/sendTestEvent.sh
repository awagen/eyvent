#!/bin/bash

curl -v -XPOST http://localhost:6001/event/store/group1 -d '{"id": "test1", "type": "click", "position": 1, "productId": "p1"}'  --header "Content-Type: application/json"
curl -v -XPOST http://localhost:6001/event/store/group1 -d '{"id": "test1", "type": "add2cart", "position": 2, "productId": "p2"}'  --header "Content-Type: application/json"
curl -v -XPOST http://localhost:6001/event/store/group1 -d '{"id": "test1", "type": "search", "query": "q1"}'  --header "Content-Type: application/json"
